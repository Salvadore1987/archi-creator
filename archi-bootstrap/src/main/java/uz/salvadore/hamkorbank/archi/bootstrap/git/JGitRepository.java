package uz.salvadore.hamkorbank.archi.bootstrap.git;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.RefKind;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ResolvedRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Репозиторий рабочего пространства на JGit, без внешнего бинарника. Локально —
 * bare-клон на пространство в каталоге кэша: {@code fetch} перед каждой операцией,
 * коммит собирается в памяти, без рабочего дерева, и уходит push'ем с ожидаемой
 * прежней головой. Принуждения нет нигде: ветка двигается только вперёд.
 */
public final class JGitRepository implements GitRepositoryPort {

    private static final String TRACKING = "refs/remotes/archi/";
    private static final int TRAILER_SEARCH_DEPTH = 1000;

    private final Path cacheDir;
    private final String username;
    private final Duration timeout;
    private final Map<WorkspaceId, Object> locks = new ConcurrentHashMap<>();

    public JGitRepository(Path cacheDir, String username, Duration timeout) {
        this.cacheDir = cacheDir;
        this.username = username;
        this.timeout = timeout;
    }

    @Override
    public Optional<GitSha> fetch(Remote remote) {
        synchronized (lock(remote)) {
            try (Repository repo = open(remote)) {
                String branchRef = Constants.R_HEADS + remote.branch();
                Optional<Ref> advertised;
                try (Transport transport = transport(repo, remote);
                        FetchConnection connection = transport.openFetch()) {
                    advertised = Optional.ofNullable(connection.getRef(branchRef));
                }
                if (advertised.isEmpty()) {
                    return Optional.empty();
                }
                try (Transport transport = transport(repo, remote)) {
                    transport.fetch(NullProgressMonitor.INSTANCE,
                            List.of(new RefSpec("+" + branchRef + ":" + TRACKING + remote.branch())));
                }
                return Optional.of(sha(advertised.get().getObjectId()));
            } catch (IOException | URISyntaxException e) {
                throw unreachable(e);
            }
        }
    }

    @Override
    public Optional<ResolvedRef> resolve(Remote remote, GitRef ref) {
        synchronized (lock(remote)) {
            try (Repository repo = open(remote)) {
                fetchAll(repo, remote);
                String name = ref.value();
                if (name.startsWith(Constants.R_HEADS)) {
                    return named(repo, TRACKING + name.substring(Constants.R_HEADS.length()), ref, RefKind.BRANCH);
                }
                if (name.startsWith(Constants.R_TAGS)) {
                    return named(repo, name, ref, RefKind.TAG);
                }
                Optional<ResolvedRef> named = named(repo, Constants.R_TAGS + name, ref, RefKind.TAG)
                        .or(() -> named(repo, TRACKING + name, ref, RefKind.BRANCH));
                return named.isPresent() || !ref.looksLikeSha() ? named : commit(repo, ref);
            } catch (IOException | URISyntaxException e) {
                throw unreachable(e);
            }
        }
    }

    /** Ссылка с этим именем в кэше — коммит за ней; аннотированный тег снимается до коммита. */
    private static Optional<ResolvedRef> named(Repository repo, String refName, GitRef ref, RefKind kind) {
        try (RevWalk walk = new RevWalk(repo)) {
            Ref found = repo.exactRef(refName);
            if (found == null) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedRef(ref, kind, sha(walk.parseCommit(found.getObjectId()))));
        } catch (IncorrectObjectTypeException notCommit) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Коммит по идентификатору, полному или сокращённому; сокращение должно быть однозначным. */
    private static Optional<ResolvedRef> commit(Repository repo, GitRef ref) throws IOException {
        try (ObjectReader reader = repo.newObjectReader(); RevWalk walk = new RevWalk(reader)) {
            List<ObjectId> candidates = new ArrayList<>(reader.resolve(AbbreviatedObjectId.fromString(ref.value())));
            List<RevCommit> commits = new ArrayList<>();
            for (ObjectId candidate : candidates) {
                parsedCommit(walk, candidate).ifPresent(commits::add);
            }
            return commits.size() == 1 ? Optional.of(new ResolvedRef(ref, RefKind.COMMIT, sha(commits.getFirst())))
                    : Optional.empty();
        }
    }

    /** Коммит-кандидат; не коммит или объекта нет — пусто: полный идентификатор разрешается и без объекта. */
    private static Optional<RevCommit> parsedCommit(RevWalk walk, ObjectId candidate) throws IOException {
        try {
            return Optional.of(walk.parseCommit(candidate));
        } catch (IncorrectObjectTypeException | MissingObjectException notCommit) {
            return Optional.empty();
        }
    }

    @Override
    public SortedMap<String, byte[]> read(Remote remote, GitSha commit, String directory) {
        synchronized (lock(remote)) {
            SortedMap<String, byte[]> files = new TreeMap<>();
            try (Repository repo = open(remote); RevWalk walk = new RevWalk(repo); TreeWalk tree = new TreeWalk(repo)) {
                tree.addTree(walk.parseCommit(id(commit)).getTree());
                tree.setRecursive(true);
                tree.setFilter(PathFilter.create(directory));
                String prefix = directory + "/";
                while (tree.next()) {
                    files.put(tree.getPathString().substring(prefix.length()),
                            repo.open(tree.getObjectId(0)).getBytes());
                }
                return files;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public Optional<byte[]> file(Remote remote, GitSha commit, String path) {
        synchronized (lock(remote)) {
            try (Repository repo = open(remote); RevWalk walk = new RevWalk(repo);
                 TreeWalk tree = TreeWalk.forPath(repo, path, walk.parseCommit(id(commit)).getTree())) {
                if (tree == null || tree.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) {
                    return Optional.empty();
                }
                return Optional.of(repo.open(tree.getObjectId(0)).getBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public List<String> directories(Remote remote, GitSha commit, String parent) {
        synchronized (lock(remote)) {
            try (Repository repo = open(remote); RevWalk walk = new RevWalk(repo);
                 TreeWalk found = TreeWalk.forPath(repo, parent, walk.parseCommit(id(commit)).getTree())) {
                if (found == null || found.getFileMode(0) != FileMode.TREE) {
                    return List.of();
                }
                List<String> names = new ArrayList<>();
                try (TreeWalk children = new TreeWalk(repo)) {
                    children.addTree(found.getObjectId(0));
                    while (children.next()) {
                        if (children.getFileMode(0) == FileMode.TREE) {
                            names.add(children.getNameString());
                        }
                    }
                }
                return names;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public boolean contains(Remote remote, GitSha ancestor, GitSha head) {
        synchronized (lock(remote)) {
            try (Repository repo = open(remote); RevWalk walk = new RevWalk(repo)) {
                if (!present(repo, remote, ancestor, head)) {
                    return false;
                }
                return walk.isMergedInto(walk.parseCommit(id(ancestor)), walk.parseCommit(id(head)));
            } catch (IOException | URISyntaxException e) {
                throw unreachable(e);
            }
        }
    }

    @Override
    public Optional<GitSha> mergeBase(Remote remote, GitSha first, GitSha second) {
        synchronized (lock(remote)) {
            try (Repository repo = open(remote); RevWalk walk = new RevWalk(repo)) {
                if (!present(repo, remote, first, second)) {
                    return Optional.empty();
                }
                walk.setRevFilter(RevFilter.MERGE_BASE);
                walk.markStart(walk.parseCommit(id(first)));
                walk.markStart(walk.parseCommit(id(second)));
                return Optional.ofNullable(walk.next()).map(JGitRepository::sha);
            } catch (IOException | URISyntaxException e) {
                throw unreachable(e);
            }
        }
    }

    /**
     * Коммиты есть в кэше: чего нет — забирается со всеми ветками и тегами. Кэш мог быть
     * пересоздан, а коммит ветки предложения — забран другим вызовом.
     */
    private boolean present(Repository repo, Remote remote, GitSha... commits) throws IOException,
            URISyntaxException {
        if (Arrays.stream(commits).allMatch(c -> has(repo, c))) {
            return true;
        }
        fetchAll(repo, remote);
        return Arrays.stream(commits).allMatch(c -> has(repo, c));
    }

    private static boolean has(Repository repo, GitSha commit) {
        try (RevWalk walk = new RevWalk(repo)) {
            walk.parseCommit(id(commit));
            return true;
        } catch (MissingObjectException | IncorrectObjectTypeException missing) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void fetchAll(Repository repo, Remote remote) throws IOException, URISyntaxException {
        try (Transport transport = transport(repo, remote)) {
            transport.fetch(NullProgressMonitor.INSTANCE, List.of(
                    new RefSpec("+" + Constants.R_HEADS + "*:" + TRACKING + "*"),
                    new RefSpec("+" + Constants.R_TAGS + "*:" + Constants.R_TAGS + "*")));
        }
    }

    @Override
    public Optional<Commit> lastChange(Remote remote, GitSha head, String directory) {
        synchronized (lock(remote)) {
            try (Repository repo = open(remote); RevWalk walk = new RevWalk(repo)) {
                walk.setTreeFilter(AndTreeFilter.create(PathFilter.create(directory), TreeFilter.ANY_DIFF));
                walk.markStart(walk.parseCommit(id(head)));
                RevCommit last = walk.next();
                return Optional.ofNullable(last).map(c -> new Commit(sha(c), trailers(c)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public Optional<GitSha> findByTrailers(Remote remote, GitSha head, Map<String, String> trailers) {
        synchronized (lock(remote)) {
            try (Repository repo = open(remote); RevWalk walk = new RevWalk(repo)) {
                walk.markStart(walk.parseCommit(id(head)));
                int seen = 0;
                for (RevCommit commit : walk) {
                    if (trailers(commit).entrySet().containsAll(trailers.entrySet())) {
                        return Optional.of(sha(commit));
                    }
                    if (++seen >= TRAILER_SEARCH_DEPTH) {
                        break;
                    }
                }
                return Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public PushResult push(Remote remote, GitSha parentSha, CommitRequest request) {
        Optional<GitSha> parent = Optional.ofNullable(parentSha);
        synchronized (lock(remote)) {
            try (Repository repo = open(remote); ObjectInserter inserter = repo.newObjectInserter();
                 RevWalk walk = new RevWalk(repo)) {
                Optional<RevTree> parentTree = parent.isPresent()
                        ? Optional.of(walk.parseCommit(id(parent.get())).getTree()) : Optional.empty();
                ObjectId tree = tree(repo, inserter, parentTree, request);
                Optional<GitSha> second = secondParent(repo, remote, walk, parent,
                        Optional.ofNullable(request.merged()));
                if (parentTree.isPresent() && tree.equals(parentTree.get()) && second.isEmpty()) {
                    return new Unchanged(parent.get());
                }
                ObjectId commit = inserter.insert(commit(tree, parent, second, request));
                inserter.flush();
                int changed = changedPaths(repo, parentTree, walk.parseTree(tree));
                String branchRef = Constants.R_HEADS + remote.branch();
                RemoteRefUpdate update = new RemoteRefUpdate(repo, commit.name(), branchRef, false, null,
                        parent.map(JGitRepository::id).orElse(ObjectId.zeroId()));
                try (Transport transport = transport(repo, remote)) {
                    org.eclipse.jgit.transport.PushResult result = transport.push(NullProgressMonitor.INSTANCE,
                            List.of(update));
                    RemoteRefUpdate.Status status = result.getRemoteUpdate(branchRef).getStatus();
                    switch (status) {
                        case OK -> {
                            RefUpdate tracking = repo.updateRef(TRACKING + remote.branch());
                            tracking.setNewObjectId(commit);
                            tracking.forceUpdate();
                            return new Pushed(sha(commit), changed);
                        }
                        case REJECTED_NONFASTFORWARD, REJECTED_REMOTE_CHANGED -> {
                            return new Rejected();
                        }
                        default -> throw new Unreachable(status + ": "
                                + Optional.ofNullable(result.getRemoteUpdate(branchRef).getMessage()).orElse(""),
                                null);
                    }
                }
            } catch (IOException | URISyntaxException e) {
                throw unreachable(e);
            }
        }
    }

    /** Дерево родителя без каталога модели и заменяемых корневых файлов, плюс новые файлы. */
    private static ObjectId tree(Repository repo, ObjectInserter inserter, Optional<RevTree> parentTree,
                                 CommitRequest request) throws IOException {
        DirCache index = DirCache.newInCore();
        DirCacheBuilder builder = index.builder();
        String prefix = request.directory() + "/";
        Set<String> rootPaths = request.rootFiles().keySet();
        if (parentTree.isPresent()) {
            try (TreeWalk walk = new TreeWalk(repo)) {
                walk.addTree(parentTree.get());
                walk.setRecursive(true);
                while (walk.next()) {
                    String path = walk.getPathString();
                    if (path.startsWith(prefix) || rootPaths.contains(path)) {
                        continue;
                    }
                    DirCacheEntry entry = new DirCacheEntry(path);
                    entry.setFileMode(walk.getFileMode(0));
                    entry.setObjectId(walk.getObjectId(0));
                    builder.add(entry);
                }
            }
        }
        Map<String, byte[]> files = new HashMap<>();
        request.files().forEach((path, bytes) -> files.put(prefix + path, bytes));
        files.putAll(request.rootFiles());
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            DirCacheEntry entry = new DirCacheEntry(file.getKey());
            entry.setFileMode(FileMode.REGULAR_FILE);
            entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, file.getValue()));
            builder.add(entry);
        }
        builder.finish();
        return index.writeTree(inserter);
    }

    /** Слитый коммит — второй родитель, если он есть в кэше и его ещё нет в истории родителя. */
    private Optional<GitSha> secondParent(Repository repo, Remote remote, RevWalk walk, Optional<GitSha> parent,
                                          Optional<GitSha> merged) throws IOException, URISyntaxException {
        if (merged.isEmpty() || !present(repo, remote, merged.get())) {
            return Optional.empty();
        }
        if (parent.isPresent() && walk.isMergedInto(walk.parseCommit(id(merged.get())),
                walk.parseCommit(id(parent.get())))) {
            return Optional.empty();
        }
        return merged;
    }

    private static CommitBuilder commit(ObjectId tree, Optional<GitSha> parent, Optional<GitSha> second,
                                        CommitRequest request) {
        CommitBuilder commit = new CommitBuilder();
        commit.setTreeId(tree);
        List<ObjectId> parents = new ArrayList<>();
        parent.ifPresent(p -> parents.add(id(p)));
        second.ifPresent(p -> parents.add(id(p)));
        commit.setParentIds(parents);
        commit.setAuthor(person(request.author()));
        commit.setCommitter(person(request.committer()));
        commit.setMessage(request.message());
        return commit;
    }

    private static PersonIdent person(Person person) {
        return new PersonIdent(person.name(), person.email(), person.when(), ZoneOffset.UTC);
    }

    private static int changedPaths(Repository repo, Optional<RevTree> before, RevTree after) throws IOException {
        if (before.isEmpty()) {
            int count = 0;
            try (TreeWalk walk = new TreeWalk(repo)) {
                walk.addTree(after);
                walk.setRecursive(true);
                while (walk.next()) {
                    count++;
                }
            }
            return count;
        }
        int count = 0;
        try (TreeWalk walk = new TreeWalk(repo)) {
            walk.addTree(before.get());
            walk.addTree(after);
            walk.setRecursive(true);
            walk.setFilter(TreeFilter.ANY_DIFF);
            while (walk.next()) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, String> trailers(RevCommit commit) {
        Map<String, String> trailers = new HashMap<>();
        for (FooterLine line : commit.getFooterLines()) {
            trailers.put(line.getKey(), line.getValue().strip());
        }
        return trailers;
    }

    private Repository open(Remote remote) throws IOException {
        Path dir = cacheDir.resolve(remote.workspace().value() + ".git");
        Files.createDirectories(cacheDir);
        Repository repo = new FileRepositoryBuilder().setGitDir(dir.toFile()).setBare().build();
        if (!repo.getObjectDatabase().exists()) {
            repo.create(true);
        }
        return repo;
    }

    private Transport transport(Repository repo, Remote remote) throws IOException, URISyntaxException {
        Transport transport = Transport.open(repo, new URIish(remote.url()));
        transport.setTimeout((int) timeout.toSeconds());
        if (remote.token() != null) {
            transport.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, remote.token()));
        }
        return transport;
    }

    private Object lock(Remote remote) {
        return locks.computeIfAbsent(remote.workspace(), w -> new Object());
    }

    private static Unreachable unreachable(Exception e) {
        return new Unreachable(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }

    private static ObjectId id(GitSha sha) {
        return ObjectId.fromString(sha.value());
    }

    private static GitSha sha(ObjectId id) {
        return GitSha.of(id.name());
    }
}
