package uz.salvadore.hamkorbank.archi.interchange.application.git;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelStates;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.RefKind;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ResolvedRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.SecretRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportApplied;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Импорт по ссылке на фейках: репозиторий — деревья коммитов в памяти, хранилища —
 * в памяти, а {@code UnitOfWork} откатывает их все при исключении, как транзакция.
 * Здесь проверяется сценарий; JGit и база — в {@code GitRefResolutionTest} и {@code GitImportIT}.
 */
class GitImportTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();
    private static final EditorIdentity ARCHITECT = EditorIdentity.of("architect-git", Role.ARCHITECT);
    private static final EditorIdentity VIEWER = EditorIdentity.of("viewer-git", Role.VIEWER);

    private final WorkspaceId workspace = new WorkspaceId(IDS.generate());
    private final ModelId model = new ModelId(IDS.generate());
    private final MemoryStates modelStates = new MemoryStates();
    private final MemorySessions sessions = new MemorySessions();
    private final MemoryOutbox outbox = new MemoryOutbox();
    private final MemoryBindings bindings = new MemoryBindings();
    private final MemoryVersions versions = new MemoryVersions();
    private final MemoryRepository repository = new MemoryRepository();
    private final List<Object> events = new ArrayList<>();
    private boolean strict;
    private GitImportService imports;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
        UnitOfWork transactions = new RollbackUnitOfWork(List.of(modelStates, sessions, outbox, bindings, versions));
        ImportPolicy policy = new ImportPolicy() {
            @Override
            public boolean strictImport(WorkspaceId workspaceId) {
                return strict;
            }

            @Override
            public void setStrictImport(WorkspaceId workspaceId, boolean value) {
                strict = value;
            }
        };
        imports = new GitImportService(bindings, repository, ref -> Optional.empty(), modelStates, sessions, policy,
                outbox, versions, transactions, events::addAll, InterchangeMetrics.NONE,
                message -> message.key() + message.args(), clock);
        bindings.save(GitBinding.configure(workspace, "file:///repo.git", Optional.empty(),
                SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of()));
        modelStates.state = new MemoryStates.State(xml(model("АБС", "")), 3, true);
    }

    @Test
    @DisplayName("INV-IXC-012: отчёт строится до применения и ничего не меняет")
    void previewReportsChangesWithoutWriting() {
        GitSha sha = repository.commit("main", model("АБС «Ядро»", NODE));
        MemoryStates.State before = modelStates.state;

        GitImportService.Preview preview = imports.preview(ARCHITECT, model, Optional.of("main"));

        assertEquals(new ResolvedRef(GitRef.of("main"), RefKind.BRANCH, sha), preview.ref());
        assertEquals(Optional.of(3L), preview.baseVersionNo());
        assertEquals(ContentHash.of(before.xml()).value(), preview.baseHash());
        assertTrue(preview.applicable());
        assertFalse(preview.unchanged());
        assertEquals(List.of(ModelChanges.Type.ADDED, ModelChanges.Type.CHANGED),
                preview.changes().changes().stream().map(ModelChanges.Change::type).toList());
        assertEquals(before, modelStates.state);
        assertTrue(sessions.rows.isEmpty());
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("INV-IXC-012: применённое состояние — новая версия; прежние версии не тронуты")
    void appliedStateBecomesNewVersion() {
        ModelDocument target = model("АБС «Ядро»", NODE);
        GitSha sha = repository.commit("main", target);
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, Optional.of("main"));

        GitImportService.Pulled pulled = imports.apply(ARCHITECT, model, confirm(preview, "из ветки"));

        assertEquals(4, pulled.versionNo());
        assertTrue(pulled.created());
        assertEquals(sha, pulled.commit());
        assertArrayEquals(xml(target), modelStates.state.xml());
        assertEquals(List.of(1L, 2L, 3L, 4L), modelStates.versionNos());
        assertEquals("из ветки", modelStates.comments.get(4L));
        ImportSession session = sessions.rows.get(pulled.session());
        assertEquals(ImportStatus.APPLIED, session.status());
        assertEquals(Optional.of(model), session.targetModelId());
        assertEquals("main@" + sha.value(), session.sourceName());
        assertEquals(1, events.size());
        assertTrue(events.getFirst() instanceof ImportApplied applied && applied.versionNo() == 4);
    }

    @Test
    @DisplayName("INV-IXC-012: сбой внутри применения откатывает всё; остаётся только сессия REJECTED")
    void applyIsAtomic() {
        repository.commit("main", model("АБС «Ядро»", NODE));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, Optional.of("main"));
        MemoryStates.State before = modelStates.state;
        modelStates.failAfterReplace = true;

        assertThrows(IllegalStateException.class, () -> imports.apply(ARCHITECT, model, confirm(preview, null)));

        assertEquals(before, modelStates.state);
        assertEquals(List.of(1L, 2L, 3L), modelStates.versionNos());
        assertEquals(1, sessions.rows.size());
        ImportSession rejected = sessions.rows.values().iterator().next();
        assertEquals(ImportStatus.REJECTED, rejected.status());
        assertEquals(InterchangeCodes.GIT_IMPORT, rejected.findings().getFirst().code());
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("INV-IXC-012: модель изменилась после отчёта — 409, применять нечего видимого")
    void staleReportIsRejected() {
        repository.commit("main", model("АБС «Ядро»", NODE));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, Optional.of("main"));
        modelStates.state = new MemoryStates.State(xml(model("АБС-2", "")), 3, true);
        MemoryStates.State edited = modelStates.state;

        InterchangeException e = assertThrows(InterchangeException.class,
                () -> imports.apply(ARCHITECT, model, confirm(preview, null)));

        assertEquals(InterchangeCodes.GIT_IMPORT, e.code());
        assertEquals(Failure.CONFLICT, e.failure());
        assertEquals(edited, modelStates.state);
        assertEquals(ImportStatus.REJECTED, sessions.rows.values().iterator().next().status());
    }

    @Test
    @DisplayName("INV-IXC-012: применяется коммит из отчёта, даже если ветка ушла вперёд")
    void reportedCommitIsAppliedAfterBranchMoved() {
        ModelDocument reported = model("АБС «Ядро»", "");
        repository.commit("main", reported);
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, Optional.of("main"));
        repository.commit("main", model("АБС «Ядро» 2", NODE));

        imports.apply(ARCHITECT, model, confirm(preview, null));

        assertArrayEquals(xml(reported), modelStates.state.xml());
    }

    @Test
    @DisplayName("UC-IXC-006: без блокировки — отказ modeling, модель не тронута, попытка в журнале")
    void lockIsRequired() {
        repository.commit("main", model("АБС «Ядро»", ""));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, Optional.of("main"));
        modelStates.state = new MemoryStates.State(modelStates.state.xml(), 3, false);

        ModelingException e = assertThrows(ModelingException.class,
                () -> imports.apply(ARCHITECT, model, confirm(preview, null)));

        assertEquals(ModelingCodes.LOCK_REQUIRED, e.code());
        assertEquals(List.of(1L, 2L, 3L), modelStates.versionNos());
        assertEquals(ModelingCodes.LOCK_REQUIRED, sessions.rows.values().iterator().next().findings().getFirst().code());
    }

    @Test
    @DisplayName("UC-IXC-006: дерево не собирается — в отчёте находка, применение — 422 и сессия REJECTED")
    void corruptTreeIsReportedAndRejected() {
        GitSha sha = repository.commitFiles("main", Map.of("model.yaml", "format_version: 9\n"));

        GitImportService.Preview preview = imports.preview(ARCHITECT, model, Optional.of("main"));
        InterchangeException e = assertThrows(InterchangeException.class, () -> imports.apply(ARCHITECT, model,
                new GitImportService.PullRequest(Optional.of("main"), Optional.of(sha.value()),
                        Optional.of(preview.baseHash()), Optional.empty())));

        assertFalse(preview.applicable());
        assertEquals(InterchangeCodes.CORRUPT_DOCUMENT, preview.findings().getFirst().code());
        assertEquals(InterchangeCodes.CORRUPT_DOCUMENT, e.code());
        assertEquals(Failure.UNPROCESSABLE, e.failure());
        assertEquals(ImportStatus.REJECTED, sessions.rows.values().iterator().next().status());
        assertEquals(List.of(1L, 2L, 3L), modelStates.versionNos());
    }

    @Test
    @DisplayName("UC-IXC-006: нет ссылки, нет каталога модели, нет привязки, нет роли — свои отказы")
    void refusals() {
        repository.commitFiles("other", Map.of());
        assertEquals(InterchangeCodes.GIT_REF_NOT_FOUND, code(() -> imports.preview(ARCHITECT, model,
                Optional.of("nope"))));
        assertEquals(InterchangeCodes.GIT_MODEL_NOT_IN_REF, code(() -> imports.preview(ARCHITECT, model,
                Optional.of("other"))));
        assertEquals(InterchangeCodes.INVALID_REQUEST, code(() -> imports.preview(ARCHITECT, model,
                Optional.of("a..b"))));
        assertEquals(InterchangeCodes.ACCESS_DENIED, code(() -> imports.preview(VIEWER, model, Optional.of("main"))));
        bindings.save(bindings.find(workspace).orElseThrow().disabled());
        assertEquals(InterchangeCodes.GIT_NOT_BOUND, code(() -> imports.preview(ARCHITECT, model,
                Optional.of("main"))));
    }

    @Test
    @DisplayName("FR-37: импорт головы ветки закрывает старые записи очереди и делает голову коммитом привязки")
    void importOfHeadSupersedesQueue() {
        outbox.enqueue(workspace, model, 2, Instant.EPOCH);
        outbox.enqueue(workspace, model, 3, Instant.EPOCH);
        GitSha head = repository.commit("main", model("АБС «Ядро»", NODE));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, Optional.of("main"));

        imports.apply(ARCHITECT, model, confirm(preview, null));

        assertEquals(List.of("SKIPPED", "SKIPPED"), outbox.statuses());
        assertEquals(Optional.of(head), bindings.find(workspace).orElseThrow().lastCommitSha());
        assertTrue(versions.shas.isEmpty(), "новую версию признает публикация, а не импорт");
    }

    @Test
    @DisplayName("FR-37: голова с тем же содержимым — версии нет, последняя получает идентификатор головы")
    void unchangedHeadIsAdoptedByLastVersion() {
        outbox.enqueue(workspace, model, 3, Instant.EPOCH);
        GitSha head = repository.commit("main", model("АБС", ""));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, Optional.of("main"));

        GitImportService.Pulled pulled = imports.apply(ARCHITECT, model, confirm(preview, null));

        assertTrue(preview.unchanged());
        assertFalse(pulled.created());
        assertEquals(3, pulled.versionNo());
        assertEquals(List.of("SKIPPED"), outbox.statuses());
        assertEquals(Map.of(3L, head), versions.shas);
    }

    @Test
    @DisplayName("FR-37: импорт тега, а не головы, очередь не трогает")
    void importOfTagKeepsQueue() {
        outbox.enqueue(workspace, model, 3, Instant.EPOCH);
        GitSha tagged = repository.commit("main", model("АБС «Ядро»", ""));
        repository.tags.put("v1", tagged);
        repository.commit("main", model("АБС «Ядро» 2", ""));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, Optional.of("v1"));

        imports.apply(ARCHITECT, model, confirm(preview, null));

        assertEquals(RefKind.TAG, preview.ref().kind());
        assertEquals(List.of("PENDING"), outbox.statuses());
    }

    // ── данные ─────────────────────────────────────────────────────────────

    private static final String NODE = "<element xsi:type=\"archimate:Node\" name=\"Сервер\" id=\"id-node\"/>";

    private static ModelDocument model(String appName, String more) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Ландшафт" id="id-model" version="5.0.0">
                  <folder name="Приложения" id="id-apps" type="application">
                    <element xsi:type="archimate:ApplicationComponent" name="%s" id="id-app"/>
                  </folder>
                  <folder name="Технологии" id="id-tech" type="technology">%s</folder>
                </archimate:model>
                """.formatted(appName, more);
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] xml(ModelDocument document) {
        return new ArchiXmlWriter().write(document);
    }

    private static GitImportService.PullRequest confirm(GitImportService.Preview preview, String comment) {
        return new GitImportService.PullRequest(Optional.of(preview.ref().ref().value()),
                Optional.of(preview.ref().sha().value()), Optional.of(preview.baseHash()), Optional.ofNullable(comment));
    }

    private static String code(Runnable call) {
        return assertThrows(InterchangeException.class, call::run).code();
    }

    // ── транзакция в памяти ─────────────────────────────────────────────────

    /** Хранилище, которое умеет снять копию состояния и вернуться к ней. */
    private interface Store<S> {

        S snapshot();

        void restore(S snapshot);
    }

    /** Запись — копия всех хранилищ на входе во внешнюю транзакцию, исключение — возврат к ней. */
    private static final class RollbackUnitOfWork implements UnitOfWork {

        private final List<Store<?>> stores;
        private int depth;

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
            } catch (RuntimeException e) {
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
            return work.get();
        }
    }

    // ── фейки портов ────────────────────────────────────────────────────────

    private final class MemoryStates implements ModelStates, Store<MemoryStates.State> {

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
            return new Current(workspace, "Ландшафт", Optional.of(state.lastVersionNo()), state.xml());
        }

        @Override
        public Replaced replace(EditorIdentity actor, ModelId m, byte[] xml, String comment) {
            if (!state.locked()) {
                throw new ModelingException(ModelingCodes.LOCK_REQUIRED, Failure.CONFLICT,
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

    private static final class MemorySessions implements ImportSessionRepository, Store<Map<ImportSessionId, ImportSession>> {

        Map<ImportSessionId, ImportSession> rows = new LinkedHashMap<>();

        @Override
        public Optional<ImportSession> find(ImportSessionId id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void save(ImportSession session) {
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

    private static final class MemoryOutbox implements GitOutbox, Store<Map<UUID, String>> {

        private record Row(UUID id, WorkspaceId workspace, ModelId model, long versionNo) {
        }

        private final List<Row> rows = new ArrayList<>();
        private Map<UUID, String> statuses = new LinkedHashMap<>();

        List<String> statuses() {
            return List.copyOf(statuses.values());
        }

        @Override
        public void enqueue(WorkspaceId workspace, ModelId model, long versionNo, Instant at) {
            Row row = new Row(IDS.generate(), workspace, model, versionNo);
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
            throw new UnsupportedOperationException();
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

    private static final class MemoryBindings implements GitBindingRepository, Store<Map<WorkspaceId, GitBinding>> {

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

    private static final class MemoryVersions implements PublishedVersions, Store<Map<Long, GitSha>> {

        Map<Long, GitSha> shas = new HashMap<>();

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
            throw new UnsupportedOperationException();
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

    /** Деревья коммитов, ветки и теги в памяти; разрешение — тег, ветка, затем коммит. */
    private final class MemoryRepository implements GitRepositoryPort {

        private final Map<GitSha, SortedMap<String, byte[]>> trees = new HashMap<>();
        private final Map<String, GitSha> branches = new HashMap<>();
        final Map<String, GitSha> tags = new HashMap<>();
        private int counter;

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
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<GitSha> mergeBase(Remote remote, GitSha first, GitSha second) {
            throw new UnsupportedOperationException();
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
        public PushResult push(Remote remote, Optional<GitSha> parent, CommitRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
