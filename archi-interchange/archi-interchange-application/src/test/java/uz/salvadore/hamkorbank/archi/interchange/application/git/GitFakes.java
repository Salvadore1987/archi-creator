package uz.salvadore.hamkorbank.archi.interchange.application.git;

import com.fasterxml.uuid.Generators;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelMerges;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelStates;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.RefKind;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ResolvedRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Фейки портов сценариев, приносящих состояние из Git: хранилища в памяти и транзакция,
 * которая откатывает их все при исключении. Общие у импорта по ссылке и слияния.
 */
final class GitFakes {

    private GitFakes() {
    }

    // ── транзакция в памяти ─────────────────────────────────────────────────

    /** Хранилище, которое умеет снять копию состояния и вернуться к ней. */
    interface Store<S> {

        S snapshot();

        void restore(S snapshot);
    }

    /** Запись — копия всех хранилищ на входе во внешнюю транзакцию, исключение — возврат к ней. */
    static final class RollbackUnitOfWork implements UnitOfWork {

        private final List<Store<?>> stores;
        private int depth;
        private int reads;

        RollbackUnitOfWork(List<Store<?>> stores) {
            this.stores = stores;
        }

        @Override
        public <T> T write(Supplier<T> work) {
            if (depth > 0) {
                return work.get();
            }
            List<Object> snapshots = stores.stream().<Object>map(Store::snapshot).toList();
            depth++;
            try {
                return work.get();
            } catch (RuntimeException e) { // NOPMD - как настоящая транзакция: откат на любой отказ
                for (int i = 0; i < stores.size(); i++) {
                    restore(stores.get(i), snapshots.get(i));
                }
                throw e;
            } finally {
                depth--;
            }
        }

        @SuppressWarnings("unchecked")
        private static <S> void restore(Store<S> store, Object snapshot) {
            store.restore((S) snapshot);
        }

        @Override
        public <T> T read(Supplier<T> work) {
            reads++;
            try {
                return work.get();
            } finally {
                reads--;
            }
        }

        /** Идёт ли сейчас транзакция — чтения или записи. */
        boolean active() {
            return depth > 0 || reads > 0;
        }
    }

    // ── фейки портов ────────────────────────────────────────────────────────

    static final class MemoryStates implements ModelStates, Store<MemoryStates.State> {

        private final WorkspaceId workspace;

        MemoryStates(WorkspaceId workspace) {
            this.workspace = workspace;
        }

        record State(byte[] xml, long lastVersionNo, boolean locked) {

            @Override
            public boolean equals(Object other) {
                return other instanceof State s && Arrays.equals(xml, s.xml) && lastVersionNo == s.lastVersionNo
                        && locked == s.locked;
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(xml);
            }
        }

        State state;
        boolean failAfterReplace;
        RuntimeException failure;
        final Map<Long, String> comments = new HashMap<>();

        List<Long> versionNos() {
            List<Long> numbers = new ArrayList<>();
            for (long no = 1; no <= state.lastVersionNo(); no++) {
                numbers.add(no);
            }
            return numbers;
        }

        @Override
        public Current current(EditorIdentity actor, ModelId m) {
            return new Current(workspace, "Ландшафт", state.lastVersionNo(), state.xml());
        }

        @Override
        public Replaced replace(EditorIdentity actor, ModelId m, byte[] xml, String comment) {
            if (!state.locked()) {
                throw new ModelingDomainException(ModelingCodes.LOCK_REQUIRED, Failure.CONFLICT,
                        Message.of(ModelingMessages.LOCK_NOT_HELD, m.value()));
            }
            if (Arrays.equals(state.xml(), xml)) {
                return new Replaced(state.lastVersionNo(), false, 0);
            }
            state = new State(xml, state.lastVersionNo() + 1, true);
            comments.put(state.lastVersionNo(), comment);
            if (failAfterReplace) {
                throw new IllegalStateException("сбой при записи");
            }
            if (failure != null) {
                throw failure;
            }
            return new Replaced(state.lastVersionNo(), true, 0);
        }

        @Override
        public State snapshot() {
            return state;
        }

        @Override
        public void restore(State snapshot) {
            state = snapshot;
        }
    }

    static final class MemorySessions implements ImportSessionRepository, Store<Map<ImportSessionId, ImportSession>> {

        Map<ImportSessionId, ImportSession> rows = new LinkedHashMap<>();
        boolean failSaves;

        @Override
        public Optional<ImportSession> find(ImportSessionId id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void save(ImportSession session) {
            if (failSaves) {
                throw new IllegalStateException("журнал недоступен");
            }
            rows.put(session.id(), session);
        }

        @Override
        public Optional<ImportSession> findByIdempotencyKey(WorkspaceId workspaceId, String key) {
            return rows.values().stream().filter(s -> s.idempotencyKey().equals(key)).findFirst();
        }

        @Override
        public Map<ImportSessionId, ImportSession> snapshot() {
            return new LinkedHashMap<>(rows);
        }

        @Override
        public void restore(Map<ImportSessionId, ImportSession> snapshot) {
            rows = snapshot;
        }
    }

    static final class MemoryOutbox implements GitOutbox, Store<Map<UUID, String>> {

        private record Row(UUID id, WorkspaceId workspace, ModelId model, long versionNo) {
        }

        private final List<Row> rows = new ArrayList<>();
        private Map<UUID, String> statuses = new LinkedHashMap<>();

        List<String> statuses() {
            return List.copyOf(statuses.values());
        }

        @Override
        public void enqueue(WorkspaceId workspace, ModelId model, long versionNo, Instant at) {
            Row row = new Row(Generators.timeBasedEpochGenerator().generate(), workspace, model, versionNo);
            rows.add(row);
            statuses.put(row.id(), "PENDING");
        }

        @Override
        public void supersede(ModelId model, long throughVersionNo, Instant at) {
            rows.stream().filter(r -> r.model().equals(model) && r.versionNo() <= throughVersionNo)
                    .filter(r -> !statuses.get(r.id()).equals("DONE"))
                    .forEach(r -> statuses.put(r.id(), "SKIPPED"));
        }

        @Override
        public List<Entry> heads() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Entry> head(ModelId model) {
            return rows.stream().filter(r -> r.model().equals(model))
                    .filter(r -> statuses.get(r.id()).equals("PENDING") || statuses.get(r.id()).equals("DIVERGED"))
                    .min(Comparator.comparingLong(Row::versionNo))
                    .map(r -> new Entry(r.id(), r.workspace(), r.model(), r.versionNo(),
                            Status.valueOf(statuses.get(r.id())), 0, null));
        }

        /** Голова очереди модели встала на расхождении. */
        void divergeHead(ModelId model) {
            head(model).ifPresent(h -> statuses.put(h.id(), "DIVERGED"));
        }

        @Override
        public int open(ModelId model) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void done(UUID id, Instant at) {
            statuses.put(id, "DONE");
        }

        @Override
        public void skipped(UUID id, Instant at) {
            statuses.put(id, "SKIPPED");
        }

        @Override
        public void diverged(UUID id, String reason, Instant at) {
            statuses.put(id, "DIVERGED");
        }

        @Override
        public void retryLater(UUID id, String reason, Instant next) {
        }

        @Override
        public Map<UUID, String> snapshot() {
            return new LinkedHashMap<>(statuses);
        }

        @Override
        public void restore(Map<UUID, String> snapshot) {
            statuses = snapshot;
        }
    }

    static final class MemoryBindings implements GitBindingRepository, Store<Map<WorkspaceId, GitBinding>> {

        private Map<WorkspaceId, GitBinding> rows = new HashMap<>();

        @Override
        public Optional<GitBinding> find(WorkspaceId workspaceId) {
            return Optional.ofNullable(rows.get(workspaceId));
        }

        @Override
        public void save(GitBinding binding) {
            rows.put(binding.workspaceId(), binding);
        }

        @Override
        public boolean workspaceExists(WorkspaceId workspaceId) {
            return true;
        }

        @Override
        public Map<WorkspaceId, GitBinding> snapshot() {
            return new HashMap<>(rows);
        }

        @Override
        public void restore(Map<WorkspaceId, GitBinding> snapshot) {
            rows = snapshot;
        }
    }

    static final class MemoryVersions implements PublishedVersions, Store<Map<Long, GitSha>> {

        Map<Long, GitSha> shas = new HashMap<>();
        Optional<Published> published = Optional.empty();

        @Override
        public Optional<Version> version(ModelId model, long versionNo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordGitSha(ModelId model, long versionNo, GitSha sha) {
            shas.put(versionNo, sha);
        }

        @Override
        public Optional<Published> lastPublished(ModelId model) {
            return published;
        }

        @Override
        public WorkspaceId visibleWorkspace(EditorIdentity actor, ModelId model) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Long, GitSha> snapshot() {
            return new HashMap<>(shas);
        }

        @Override
        public void restore(Map<Long, GitSha> snapshot) {
            shas = snapshot;
        }
    }

    /**
     * Деревья коммитов с родителями, ветки и теги в памяти; разрешение — тег, ветка,
     * затем коммит. Новый коммит ветки — потомок её головы.
     */
    static final class MemoryRepository implements GitRepositoryPort {

        private final ModelId model;
        private final Map<GitSha, SortedMap<String, byte[]>> trees = new HashMap<>();
        private final Map<GitSha, List<GitSha>> parents = new HashMap<>();
        private final Map<String, GitSha> branches = new HashMap<>();
        final Map<String, GitSha> tags = new HashMap<>();
        private int counter;

        MemoryRepository(ModelId model) {
            this.model = model;
        }

        /** Ветка от коммита, как {@code git branch name commit}. */
        void branch(String name, GitSha from) {
            branches.put(name, from);
        }

        GitSha commit(String branch, ModelDocument document) {
            SortedMap<String, byte[]> files = new TreeMap<>();
            new GitYamlWriter().write(document, GitPositions.none()).forEach(files::put);
            return commitTree(branch, files);
        }

        GitSha commitFiles(String branch, Map<String, String> files) {
            SortedMap<String, byte[]> tree = new TreeMap<>();
            files.forEach((path, text) -> tree.put(path, text.getBytes(StandardCharsets.UTF_8)));
            return files.isEmpty() ? commitRaw(branch, new TreeMap<>()) : commitTree(branch, tree);
        }

        private GitSha commitTree(String branch, SortedMap<String, byte[]> modelFiles) {
            SortedMap<String, byte[]> tree = new TreeMap<>();
            String directory = GitRemotes.MODELS_DIR + "/landshaft." + GitRemotes.shortId(model) + "/";
            modelFiles.forEach((path, bytes) -> tree.put(directory + path, bytes));
            return commitRaw(branch, tree);
        }

        private GitSha commitRaw(String branch, SortedMap<String, byte[]> tree) {
            GitSha sha = GitSha.of(ContentHash.of(("commit" + ++counter).getBytes(StandardCharsets.UTF_8)).value()
                    .substring(0, 40));
            trees.put(sha, tree);
            parents.put(sha, Optional.ofNullable(branches.get(branch)).map(List::of).orElseGet(List::of));
            branches.put(branch, sha);
            return sha;
        }

        @Override
        public Optional<GitSha> fetch(Remote remote) {
            return Optional.ofNullable(branches.get(remote.branch()));
        }

        @Override
        public Optional<ResolvedRef> resolve(Remote remote, GitRef ref) {
            if (tags.containsKey(ref.value())) {
                return Optional.of(new ResolvedRef(ref, RefKind.TAG, tags.get(ref.value())));
            }
            if (branches.containsKey(ref.value())) {
                return Optional.of(new ResolvedRef(ref, RefKind.BRANCH, branches.get(ref.value())));
            }
            List<GitSha> matches = trees.keySet().stream().filter(s -> s.value().startsWith(ref.value())).toList();
            return ref.looksLikeSha() && matches.size() == 1
                    ? Optional.of(new ResolvedRef(ref, RefKind.COMMIT, matches.getFirst())) : Optional.empty();
        }

        @Override
        public SortedMap<String, byte[]> read(Remote remote, GitSha commit, String directory) {
            SortedMap<String, byte[]> files = new TreeMap<>();
            String prefix = directory + "/";
            trees.get(commit).forEach((path, bytes) -> {
                if (path.startsWith(prefix)) {
                    files.put(path.substring(prefix.length()), bytes);
                }
            });
            return files;
        }

        @Override
        public Optional<byte[]> file(Remote remote, GitSha commit, String path) {
            return Optional.ofNullable(trees.get(commit).get(path));
        }

        @Override
        public List<String> directories(Remote remote, GitSha commit, String parent) {
            String prefix = parent + "/";
            return trees.get(commit).keySet().stream().filter(p -> p.startsWith(prefix))
                    .map(p -> p.substring(prefix.length())).filter(p -> p.contains("/"))
                    .map(p -> p.substring(0, p.indexOf('/'))).distinct().toList();
        }

        @Override
        public boolean contains(Remote remote, GitSha ancestor, GitSha head) {
            return ancestors(head).contains(ancestor);
        }

        @Override
        public Optional<GitSha> mergeBase(Remote remote, GitSha first, GitSha second) {
            Set<GitSha> common = ancestors(second);
            return ancestors(first).stream().filter(common::contains).findFirst();
        }

        /** Коммит и его предки, ближние первыми. */
        private LinkedHashSet<GitSha> ancestors(GitSha commit) {
            LinkedHashSet<GitSha> seen = new LinkedHashSet<>();
            ArrayDeque<GitSha> queue = new ArrayDeque<>(List.of(commit));
            while (!queue.isEmpty()) {
                GitSha next = queue.poll();
                if (seen.add(next)) {
                    queue.addAll(parents.getOrDefault(next, List.of()));
                }
            }
            return seen;
        }

        @Override
        public Optional<Commit> lastChange(Remote remote, GitSha head, String directory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<GitSha> findByTrailers(Remote remote, GitSha head, Map<String, String> trailers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PushResult push(Remote remote, GitSha parent, CommitRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    static final class MemoryMerges implements ModelMerges, Store<Map<ModelId, GitSha>> {

        Map<ModelId, GitSha> rows = new HashMap<>();

        @Override
        public void record(ModelId model, GitSha sha, Instant at) {
            rows.put(model, sha);
        }

        @Override
        public Optional<GitSha> last(ModelId model) {
            return Optional.ofNullable(rows.get(model));
        }

        @Override
        public Map<ModelId, GitSha> snapshot() {
            return new HashMap<>(rows);
        }

        @Override
        public void restore(Map<ModelId, GitSha> snapshot) {
            rows = snapshot;
        }
    }
}
