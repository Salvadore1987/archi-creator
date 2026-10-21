package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPositions;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublisher;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublisher.ModelGitStatus;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlWriter;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelMerges;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitAuthor;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBindingSnapshotCommittedEvent;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBindingStatus;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.SecretRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Публикация версий в Git на настоящем JGit поверх локального bare-репозитория:
 * только вперёд, расхождение не перезаписывается, повтор не дублирует коммит.
 * База и очередь — в памяти: здесь проверяется сценарий и адаптер, а не хранение
 * (его — {@code GitExportIT}).
 */
class GitPublisherTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();
    private static final EditorIdentity ARCHITECT = EditorIdentity.of("architect-git", Role.ARCHITECT);

    @TempDir
    Path temp;

    private final WorkspaceId workspace = new WorkspaceId(IDS.generate());
    private final ModelId model = new ModelId(IDS.generate());
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T10:00:00Z"));
    private final MemoryOutbox outbox = new MemoryOutbox();
    private final MemoryBindings bindings = new MemoryBindings();
    private final MemoryVersions versions = new MemoryVersions();
    private final Map<ModelId, GitSha> merged = new HashMap<>();
    private final ModelMerges merges = new ModelMerges() {
        @Override
        public void record(ModelId m, GitSha sha, Instant at) {
            merged.put(m, sha);
        }

        @Override
        public Optional<GitSha> last(ModelId m) {
            return Optional.ofNullable(merged.get(m));
        }
    };
    private final List<Object> events = new ArrayList<>();
    private Path remote;
    /** Что случается сразу после push — например, администратор перепривязывает пространство. */
    private Runnable afterPush = () -> { };
    private GitRepositoryPort repository;
    private GitPublisher publishing;

    @BeforeEach
    void setUp() throws Exception {
        remote = temp.resolve("remote.git");
        Git.init().setBare(true).setDirectory(remote.toFile()).setInitialBranch("main").call().close();
        GitRepositoryPort jgit = new JGitRepository(temp.resolve("cache"), "oauth2", Duration.ofSeconds(10));
        repository = (GitRepositoryPort) Proxy.newProxyInstance(GitRepositoryPort.class.getClassLoader(),
                new Class<?>[] {GitRepositoryPort.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(jgit, args);
                        if (method.getName().equals("push")) {
                            afterPush.run();
                        }
                        return result;
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        UnitOfWork direct = new UnitOfWork() {
            @Override
            public <T> T write(Supplier<T> work) {
                return work.get();
            }

            @Override
            public <T> T read(Supplier<T> work) {
                return work.get();
            }
        };
        publishing = new GitPublisher(outbox, bindings, versions, merges, repository, ref -> Optional.empty(),
                direct,
                events::addAll, InterchangeMetrics.NONE, message -> message.key() + message.args(), clock,
                new GitPublisher.Settings("Archi Creator", "archi@test", "users.test", Duration.ofSeconds(5),
                        Duration.ofMinutes(10), Duration.ofDays(30)));
        bindings.save(GitBinding.configure(workspace, remote.toUri().toString(), null,
                SecretRef.of("env:ARCHI_GIT_TOKEN"),
                List.of(new GitAuthor("architect-git", "Архитектор Тестов", "architect@bank.uz"))));
    }

    @Test
    @DisplayName("INV-IXC-009: каждая версия — коммит поверх предыдущего, ветка двигается только вперёд")
    void pushIsAlwaysFastForward() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");
        commitVersion(2, renamed("Клиент", "Клиент банка"), "Вторая");

        assertThat(publishing.publishPending()).isEqualTo(2);

        List<RevCommit> history = history();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getParent(0)).isEqualTo(history.get(1));
        assertThat(versions.sha(2)).contains(history.get(0).name());

        GitRepositoryPort.Remote target = new GitRepositoryPort.Remote(workspace, remote.toUri().toString(), "main",
                null);
        GitRepositoryPort.PushResult stale = repository.push(target, GitSha.of(history.get(1).name()),
                new GitRepositoryPort.CommitRequest("models/x", new TreeMap<>(Map.of("a.yaml", bytes("a: 1\n"))),
                        new TreeMap<>(), "stale", person(), person()));

        assertThat(stale).isInstanceOf(GitRepositoryPort.Rejected.class);
        assertThat(head()).isEqualTo(history.get(0).getId());
    }

    @Test
    @DisplayName("FR-37: автор — архитектор, сообщение — комментарий, трейлеры и .gitattributes на месте")
    void commitCarriesAuthorMessageAndTrailers() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Импорт ландшафта");

        publishing.publishPending();

        RevCommit commit = history().getFirst();
        assertThat(commit.getAuthorIdent().getName()).isEqualTo("Архитектор Тестов");
        assertThat(commit.getAuthorIdent().getEmailAddress()).isEqualTo("architect@bank.uz");
        assertThat(commit.getCommitterIdent().getName()).isEqualTo("Archi Creator");
        assertThat(commit.getFullMessage()).startsWith("Импорт ландшафта\n\n")
                .contains("Archi-Model-Id: " + model.value()).contains("Archi-Version: 1");
        SortedMap<String, byte[]> tree = tree(commit);
        assertThat(tree).containsKey(".gitattributes");
        assertThat(new String(tree.get(".gitattributes"), StandardCharsets.UTF_8)).contains("models/** -merge");
        assertThat(tree.keySet())
                .anyMatch(p -> p.matches("models/neizvestnye-uzly-i-atributy\\.[0-9a-f]{8}/model\\.yaml"));
        assertThat(events).singleElement().isInstanceOf(GitBindingSnapshotCommittedEvent.class);
    }

    @Test
    @DisplayName("FR-37: чужой коммит вне каталога модели — наш ложится сверху, чужое не трогается")
    void foreignCommitOutsideModelIsKept() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");
        publishing.publishPending();
        ObjectId foreign = externalCommit("README.md", "# Ландшафт\n");

        commitVersion(2, renamed("Клиент", "Клиент банка"), "Вторая");
        publishing.publishPending();

        RevCommit top = history().getFirst();
        assertThat(top.getParent(0).getId()).isEqualTo(foreign);
        assertThat(tree(top)).containsKey("README.md");
        assertThat(versions.sha(2)).contains(top.name());
    }

    @Test
    @DisplayName("FR-37, INV-IXC-009: чужая правка каталога модели — расхождение, чужой коммит не перезаписан")
    void divergedModelIsNotOverwritten() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");
        publishing.publishPending();
        String directory = tree(history().getFirst()).keySet().stream().filter(p -> p.endsWith("/model.yaml"))
                .findFirst().orElseThrow().replace("/model.yaml", "");
        ObjectId foreign = externalCommit(directory + "/notes.yaml", "правка руками\n");

        commitVersion(2, renamed("Клиент", "Клиент банка"), "Вторая");
        commitVersion(3, renamed("Клиент", "Клиент банка 2"), "Третья");
        publishing.publishPending();

        assertThat(head()).isEqualTo(foreign);
        assertThat(versions.sha(2)).isEmpty();
        ModelGitStatus status = publishing.status(model);
        assertThat(status.state()).isEqualTo(ModelGitStatus.State.DIVERGED);
        assertThat(status.divergedVersionNo()).isEqualTo(2L);
        assertThat(status.pending()).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-37: чужая правка каталога, уже вошедшая в опубликованный коммит модели, — не расхождение")
    void foreignChangeAlreadyPublishedIsNotDivergence() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");
        publishing.publishPending();
        ObjectId foreign = externalCommit(modelDirectory() + "/notes.yaml", "правка руками\n");
        versions.recordGitSha(model, 1, GitSha.of(foreign.name()));

        commitVersion(2, renamed("Клиент", "Клиент банка"), "Вторая");
        publishing.publishPending();

        RevCommit top = history().getFirst();
        assertThat(top.getParent(0).getId()).isEqualTo(foreign);
        assertThat(versions.sha(2)).contains(top.name());
        assertThat(publishing.status(model).state()).isEqualTo(ModelGitStatus.State.SYNCED);
    }

    @Test
    @DisplayName("FR-39: чужая правка каталога, принятая слиянием, — не расхождение, коммит ложится сверху")
    void mergedForeignChangeIsNotDivergence() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");
        publishing.publishPending();
        ObjectId foreign = externalCommit(modelDirectory() + "/notes.yaml", "правка руками\n");
        merges.record(model, GitSha.of(foreign.name()), clock.instant());

        commitVersion(2, renamed("Клиент", "Клиент банка"), "Вторая");
        publishing.publishPending();

        RevCommit top = history().getFirst();
        assertThat(top.getParents()).extracting(RevCommit::getId).containsExactly(foreign);
        assertThat(versions.sha(2)).contains(top.name());
        assertThat(publishing.status(model).state()).isEqualTo(ModelGitStatus.State.SYNCED);
    }

    @Test
    @DisplayName("FR-39, INV-IXC-009: слитая ветка предложения — второй родитель, ветка привязки её содержит")
    void mergedProposalBecomesSecondParent() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");
        publishing.publishPending();
        ObjectId proposal = proposalCommit(modelDirectory() + "/notes.yaml", "предложение\n");
        merges.record(model, GitSha.of(proposal.name()), clock.instant());

        commitVersion(2, renamed("Клиент", "Клиент банка"), "Вторая");
        publishing.publishPending();

        RevCommit top = history().getFirst();
        assertThat(top.getParentCount()).isEqualTo(2);
        assertThat(top.getParent(1).getId()).isEqualTo(proposal);
        assertThat(versions.sha(2)).contains(top.name());

        commitVersion(3, renamed("Клиент", "Клиент банка 2"), "Третья");
        publishing.publishPending();
        assertThat(history().getFirst().getParentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-37: каталог в голове уже несёт содержимое версии — версия получает голову без коммита")
    void headCarryingVersionIsAdopted() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");
        publishing.publishPending();
        String directory = modelDirectory();
        byte[] second = renamed("Клиент", "Клиент банка");
        ObjectId foreign = externalTree(directory, new GitYamlWriter().write(
                new StaxArchiDocumentReader().read(new ByteArrayInputStream(second)), GitPositions.none()));

        versions.add(new PublishedVersions.Version(workspace, "Неизвестные узлы и атрибуты", 2, "architect-git",
                "Вторая", clock.instant(), canonicalHash(second), second));
        outbox.enqueue(workspace, model, 2, clock.instant());
        publishing.publishPending();

        assertThat(head()).isEqualTo(foreign);
        assertThat(versions.sha(2)).contains(foreign.name());
        assertThat(publishing.status(model).state()).isEqualTo(ModelGitStatus.State.SYNCED);
    }

    @Test
    @DisplayName("INV-IXC-009: переписанная история — расхождение, а не публикация поверх")
    void rewrittenHistoryIsDivergence() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");
        publishing.publishPending();
        Path orphan = Files.createTempDirectory(temp, "orphan");
        try (Git git = Git.init().setDirectory(orphan.toFile()).setInitialBranch("main").call()) {
            Files.writeString(orphan.resolve("README.md"), "новая история\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("история с нуля").setAuthor(new PersonIdent("Чужой", "other@bank.uz"))
                    .setCommitter(new PersonIdent("Чужой", "other@bank.uz")).call();
            git.push().setRemote(remote.toUri().toString()).setRefSpecs(new RefSpec("+refs/heads/main:refs/heads/main"))
                    .call();
        }

        commitVersion(2, renamed("Клиент", "Клиент банка"), "Вторая");
        publishing.publishPending();

        assertThat(publishing.status(model).state()).isEqualTo(ModelGitStatus.State.DIVERGED);
    }

    @Test
    @DisplayName("ADR-0003: повтор после сбоя между push и записью git_sha находит коммит и не дублирует его")
    void retryAdoptsCommitByTrailers() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");
        publishing.publishPending();
        String sha = versions.sha(1).orElseThrow();
        versions.forget(1);
        outbox.reopen();

        publishing.publishPending();

        assertThat(history()).hasSize(1);
        assertThat(versions.sha(1)).contains(sha);
    }

    @Test
    @DisplayName("FR-40: версия с тем же содержимым коммита не создаёт, получает голову ветки")
    void unchangedContentMakesNoCommit() throws Exception {
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");
        commitVersion(2, Fixtures.fixture("unknown_extension"), "Откат к первой");

        publishing.publishPending();

        assertThat(history()).hasSize(1);
        assertThat(versions.sha(2)).isEqualTo(versions.sha(1));
    }

    @Test
    @DisplayName("FR-47: недоступный репозиторий — версия ждёт, привязка UNREACHABLE; "
            + "после восстановления — публикуется")
    void unreachableRepositoryDefersPublication() throws Exception {
        Path missing = temp.resolve("later.git");
        bindings.save(GitBinding.configure(workspace, missing.toUri().toString(), null,
                SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of()));
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");

        assertThat(publishing.publishPending()).isZero();
        assertThat(bindings.find(workspace).orElseThrow().status()).isEqualTo(GitBindingStatus.UNREACHABLE);
        assertThat(publishing.status(model).state()).isEqualTo(ModelGitStatus.State.PENDING);
        assertThatThrownBy(() -> publishing.push(ARCHITECT, model)).isInstanceOf(InterchangeException.class)
                .hasMessageContaining("IXC_GIT_UNREACHABLE");

        Git.init().setBare(true).setDirectory(missing.toFile()).setInitialBranch("main").call().close();
        clock.advance(Duration.ofMinutes(1));

        assertThat(publishing.publishPending()).isEqualTo(1);
        assertThat(bindings.find(workspace).orElseThrow().status()).isEqualTo(GitBindingStatus.CONFIGURED);
    }

    @Test
    @DisplayName("FR-37: привязку сменили, пока шёл push, — новая привязка не получает голову старого репозитория")
    void rebindingDuringPushKeepsNewBindingHead() {
        GitBinding rebound = GitBinding.configure(workspace, temp.resolve("other.git").toUri().toString(), null,
                SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of());
        afterPush = () -> bindings.save(rebound);
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");

        assertThat(publishing.publishPending()).isEqualTo(1);

        assertThat(bindings.find(workspace).orElseThrow().lastCommitSha()).isNull();
        assertThat(versions.sha(1)).isPresent();
    }

    @Test
    @DisplayName("Сбой одной модели откладывает только её запись — очередь остальных моделей идёт дальше")
    void failingModelDoesNotStopTheQueue() throws Exception {
        ModelId broken = new ModelId(IDS.generate());
        versions.failing = broken;
        outbox.enqueue(workspace, broken, 1, clock.instant());
        commitVersion(1, Fixtures.fixture("unknown_extension"), "Первая");

        assertThat(publishing.publishPending()).isEqualTo(1);

        assertThat(history()).hasSize(1);
        GitOutbox.Entry deferred = outbox.head(broken).orElseThrow();
        assertThat(deferred.attempts()).isEqualTo(1);
        assertThat(deferred.nextAttemptAt()).isAfter(clock.instant());
    }

    private void commitVersion(long versionNo, byte[] xml, String comment) {
        versions.add(new PublishedVersions.Version(workspace, "Неизвестные узлы и атрибуты", versionNo,
                "architect-git", comment, clock.instant(), "0".repeat(64), xml));
        outbox.enqueue(workspace, model, versionNo, clock.instant());
    }

    private static byte[] renamed(String from, String to) {
        return new String(Fixtures.fixture("unknown_extension"), StandardCharsets.UTF_8)
                .replace("name=\"" + from + "\"", "name=\"" + to + "\"").getBytes(StandardCharsets.UTF_8);
    }

    private String modelDirectory() throws Exception {
        return tree(history().getFirst()).keySet().stream().filter(p -> p.endsWith("/model.yaml"))
                .findFirst().orElseThrow().replace("/model.yaml", "");
    }

    private static String canonicalHash(byte[] xml) {
        return ContentHash.of(new ArchiXmlWriter().write(new StaxArchiDocumentReader()
                .read(new ByteArrayInputStream(xml)))).value();
    }

    /** Каталог модели целиком заменяется файлами — чужим коммитом с другими позициями. */
    private ObjectId externalTree(String directory, SortedMap<String, byte[]> files) throws Exception {
        Path work = Files.createTempDirectory(temp, "work");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(work.toFile()).call()) {
            git.rm().addFilepattern(directory).call();
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                Path target = work.resolve(directory).resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.write(target, file.getValue());
            }
            git.add().addFilepattern(directory).call();
            RevCommit commit = git.commit().setMessage("принято в ревью")
                    .setAuthor(new PersonIdent("Чужой", "other@bank.uz")).setCommitter(new PersonIdent("Чужой",
                            "other@bank.uz")).call();
            git.push().setRemote("origin").add("main").call();
            return commit.getId();
        }
    }

    private ObjectId externalCommit(String path, String content) throws Exception {
        Path work = Files.createTempDirectory(temp, "work");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(work.toFile()).call()) {
            Path file = work.resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            git.add().addFilepattern(path).call();
            RevCommit commit = git.commit().setMessage("правка не через сервер")
                    .setAuthor(new PersonIdent("Чужой", "other@bank.uz")).setCommitter(new PersonIdent("Чужой",
                            "other@bank.uz")).call();
            git.push().setRemote("origin").add("main").call();
            return commit.getId();
        }
    }

    /** Коммит в ветку предложения от головы {@code main}, как merge request. */
    private ObjectId proposalCommit(String path, String content) throws Exception {
        Path work = Files.createTempDirectory(temp, "work");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(work.toFile()).call()) {
            git.checkout().setCreateBranch(true).setName("proposal").call();
            Path file = work.resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            git.add().addFilepattern(path).call();
            RevCommit commit = git.commit().setMessage("предложение")
                    .setAuthor(new PersonIdent("Чужой", "other@bank.uz")).setCommitter(new PersonIdent("Чужой",
                            "other@bank.uz")).call();
            git.push().setRemote("origin").add("proposal").call();
            return commit.getId();
        }
    }

    private List<RevCommit> history() throws Exception {
        try (Git git = Git.open(remote.toFile())) {
            List<RevCommit> commits = new ArrayList<>();
            git.log().add(git.getRepository().resolve("refs/heads/main")).call().forEach(commits::add);
            return commits;
        }
    }

    private ObjectId head() throws Exception {
        try (Git git = Git.open(remote.toFile())) {
            return git.getRepository().resolve("refs/heads/main");
        }
    }

    private SortedMap<String, byte[]> tree(RevCommit commit) throws Exception {
        SortedMap<String, byte[]> files = new TreeMap<>();
        try (Git git = Git.open(remote.toFile()); RevWalk walk = new RevWalk(git.getRepository());
             TreeWalk tree = new TreeWalk(git.getRepository())) {
            tree.addTree(walk.parseCommit(commit).getTree());
            tree.setRecursive(true);
            while (tree.next()) {
                files.put(tree.getPathString(), git.getRepository().open(tree.getObjectId(0)).getBytes());
            }
        }
        return files;
    }

    private GitRepositoryPort.Person person() {
        return new GitRepositoryPort.Person("t", "t@t", clock.instant());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Очередь в памяти с тем же порядком, что у таблицы: голова модели — меньший номер версии. */
    private static final class MemoryOutbox implements GitOutbox {

        private final Map<UUID, Row> rows = new HashMap<>();

        private static final class Row {
            UUID id;
            WorkspaceId workspace;
            ModelId model;
            long versionNo;
            String status = "PENDING";
            int attempts;
            Instant next;
        }

        void reopen() {
            rows.values().forEach(r -> r.status = "PENDING");
        }

        @Override
        public void enqueue(WorkspaceId workspace, ModelId model, long versionNo, Instant at) {
            Row row = new Row();
            row.id = IDS.generate();
            row.workspace = workspace;
            row.model = model;
            row.versionNo = versionNo;
            rows.put(row.id, row);
        }

        private List<Row> open() {
            return rows.values().stream().filter(r -> r.status.equals("PENDING") || r.status.equals("DIVERGED"))
                    .sorted(Comparator.comparingLong(r -> r.versionNo)).toList();
        }

        @Override
        public List<Entry> heads() {
            Map<ModelId, Entry> heads = new HashMap<>();
            open().forEach(r -> heads.putIfAbsent(r.model, entry(r)));
            return List.copyOf(heads.values());
        }

        @Override
        public Optional<Entry> head(ModelId model) {
            return open().stream().filter(r -> r.model.equals(model)).findFirst().map(MemoryOutbox::entry);
        }

        @Override
        public int open(ModelId model) {
            return (int) open().stream().filter(r -> r.model.equals(model)).count();
        }

        @Override
        public void done(UUID id, Instant at) {
            rows.get(id).status = "DONE";
        }

        @Override
        public void skipped(UUID id, Instant at) {
            rows.get(id).status = "SKIPPED";
        }

        @Override
        public void diverged(UUID id, String reason, Instant at) {
            rows.get(id).status = "DIVERGED";
            rows.get(id).attempts++;
        }

        @Override
        public void supersede(ModelId model, long throughVersionNo, Instant at) {
            open().stream().filter(r -> r.model.equals(model) && r.versionNo <= throughVersionNo)
                    .forEach(r -> r.status = "SKIPPED");
        }

        @Override
        public void retryLater(UUID id, String reason, Instant next) {
            rows.get(id).attempts++;
            rows.get(id).next = next;
        }

        @Override
        public int purgeClosed(Instant before) {
            return 0;
        }

        private static Entry entry(Row r) {
            return new Entry(r.id, r.workspace, r.model, r.versionNo, Status.valueOf(r.status), r.attempts,
                    r.next);
        }
    }

    private static final class MemoryBindings implements GitBindingRepository {

        private final Map<WorkspaceId, GitBinding> bindings = new HashMap<>();

        @Override
        public Optional<GitBinding> find(WorkspaceId workspaceId) {
            return Optional.ofNullable(bindings.get(workspaceId));
        }

        @Override
        public void save(GitBinding binding) {
            bindings.put(binding.workspaceId(), binding);
        }

        @Override
        public boolean workspaceExists(WorkspaceId workspaceId) {
            return true;
        }
    }

    private final class MemoryVersions implements PublishedVersions {

        private final Map<Long, Version> versions = new HashMap<>();
        private final Map<Long, GitSha> shas = new HashMap<>();
        private ModelId failing;

        void add(Version version) {
            versions.put(version.versionNo(), version);
        }

        Optional<String> sha(long versionNo) {
            return Optional.ofNullable(shas.get(versionNo)).map(GitSha::value);
        }

        void forget(long versionNo) {
            shas.remove(versionNo);
        }

        @Override
        public Optional<Version> version(ModelId m, long versionNo) {
            if (m.equals(failing)) {
                throw new IllegalStateException("snapshot store failed");
            }
            return Optional.ofNullable(versions.get(versionNo));
        }

        @Override
        public void recordGitSha(ModelId m, long versionNo, GitSha sha) {
            shas.put(versionNo, sha);
        }

        @Override
        public Optional<Published> lastPublished(ModelId m) {
            return shas.entrySet().stream().max(Map.Entry.comparingByKey())
                    .map(e -> new Published(e.getKey(), e.getValue()));
        }

        @Override
        public WorkspaceId visibleWorkspace(EditorIdentity actor, ModelId m) {
            return workspace;
        }
    }
}
