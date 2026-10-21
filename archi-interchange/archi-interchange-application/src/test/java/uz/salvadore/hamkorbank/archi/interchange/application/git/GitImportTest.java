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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryBindings;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemorySessions;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryStates;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.RollbackUnitOfWork;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.RefKind;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ResolvedRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.SecretRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSessionAppliedEvent;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
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
    private final MemoryStates modelStates = new MemoryStates(workspace);
    private final MemorySessions sessions = new MemorySessions();
    private final MemoryOutbox outbox = new MemoryOutbox();
    private final MemoryBindings bindings = new MemoryBindings();
    private final MemoryVersions versions = new MemoryVersions();
    private final MemoryRepository repository = new MemoryRepository(model);
    private final List<Object> events = new ArrayList<>();
    private boolean strict;
    private GitImportService imports;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
        RollbackUnitOfWork transactions = new RollbackUnitOfWork(List.of(modelStates, sessions, outbox, bindings,
                versions));
        ImportPolicy policy = new ImportPolicy() {
            @Override
            public boolean strictImport(WorkspaceId workspaceId) {
                assertTrue(transactions.active(), "настройка пространства читается в транзакции");
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
        bindings.save(GitBinding.configure(workspace, "file:///repo.git", null,
                SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of()));
        modelStates.state = new MemoryStates.State(xml(model("АБС", "")), 3, true);
    }

    @Test
    @DisplayName("INV-IXC-012: отчёт строится до применения и ничего не меняет")
    void previewReportsChangesWithoutWriting() {
        GitSha sha = repository.commit("main", model("АБС «Ядро»", NODE));
        MemoryStates.State before = modelStates.state;

        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");

        assertEquals(new ResolvedRef(GitRef.of("main"), RefKind.BRANCH, sha), preview.ref());
        assertEquals(3L, preview.baseVersionNo());
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
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");

        GitImportService.Pulled pulled = imports.apply(ARCHITECT, model, confirm(preview, "из ветки"));

        assertEquals(4, pulled.versionNo());
        assertTrue(pulled.created());
        assertEquals(sha, pulled.commit());
        assertArrayEquals(xml(target), modelStates.state.xml());
        assertEquals(List.of(1L, 2L, 3L, 4L), modelStates.versionNos());
        assertEquals("из ветки", modelStates.comments.get(4L));
        ImportSession session = sessions.rows.get(pulled.session());
        assertEquals(ImportStatus.APPLIED, session.status());
        assertEquals(model, session.targetModelId());
        assertEquals("main@" + sha.value(), session.sourceName());
        assertEquals(1, events.size());
        assertTrue(events.getFirst() instanceof ImportSessionAppliedEvent applied && applied.versionNo() == 4);
    }

    @Test
    @DisplayName("INV-IXC-012: сбой внутри применения откатывает всё; остаётся только сессия REJECTED")
    void applyIsAtomic() {
        repository.commit("main", model("АБС «Ядро»", NODE));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");
        MemoryStates.State before = modelStates.state;
        modelStates.failAfterReplace = true;

        assertThrows(IllegalStateException.class, () -> imports.apply(ARCHITECT, model, confirm(preview, null)));

        assertEquals(before, modelStates.state);
        assertEquals(List.of(1L, 2L, 3L), modelStates.versionNos());
        assertEquals(1, sessions.rows.size());
        ImportSession rejected = sessions.rows.values().iterator().next();
        assertEquals(ImportStatus.REJECTED, rejected.status());
        assertEquals(InterchangeCodes.GIT_APPLY_FAILED, rejected.findings().getFirst().code());
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("UC-IXC-006: журнал попытки не записался — наружу исходный отказ, сбой журнала приложен к нему")
    void journalFailureDoesNotHideRefusal() {
        repository.commit("main", model("АБС «Ядро»", NODE));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");
        modelStates.failAfterReplace = true;
        sessions.failSaves = true;

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> imports.apply(ARCHITECT, model, confirm(preview, null)));

        assertEquals("сбой при записи", e.getMessage());
        assertEquals(1, e.getSuppressed().length);
    }

    @Test
    @DisplayName("UC-IXC-006: отказ домена interchange при записи — в журнале его код, а не «отчёт устарел»")
    void domainRefusalKeepsItsCodeInJournal() {
        repository.commit("main", model("АБС «Ядро»", NODE));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");
        modelStates.failure = new InterchangeDomainException(InterchangeCodes.GIT_BINDING_DISABLED,
                Message.of(InterchangeMessages.GIT_BINDING_MISSING, workspace));

        assertThrows(InterchangeDomainException.class, () -> imports.apply(ARCHITECT, model, confirm(preview, null)));

        assertEquals(InterchangeCodes.GIT_BINDING_DISABLED,
                sessions.rows.values().iterator().next().findings().getFirst().code());
    }

    @Test
    @DisplayName("UC-IXC-006: отказ строгого режима при применении — IXC_STRICT_IMPORT_REJECTED, а не «повреждён»")
    void strictRejectionKeepsItsCode() {
        strict = true;
        repository.commit("main", model("АБС «Ядро»", NODE + FORBIDDEN));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");

        InterchangeException e = assertThrows(InterchangeException.class,
                () -> imports.apply(ARCHITECT, model, confirm(preview, null)));

        assertFalse(preview.applicable());
        assertEquals(InterchangeCodes.STRICT_IMPORT_REJECTED, e.code());
        assertEquals(Failure.UNPROCESSABLE, e.failure());
        assertEquals(ImportStatus.REJECTED, sessions.rows.values().iterator().next().status());
    }

    @Test
    @DisplayName("INV-IXC-012: модель изменилась после отчёта — 409, применять нечего видимого")
    void staleReportIsRejected() {
        repository.commit("main", model("АБС «Ядро»", NODE));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");
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
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");
        repository.commit("main", model("АБС «Ядро» 2", NODE));

        imports.apply(ARCHITECT, model, confirm(preview, null));

        assertArrayEquals(xml(reported), modelStates.state.xml());
    }

    @Test
    @DisplayName("UC-IXC-006: без блокировки — отказ modeling, модель не тронута, попытка в журнале")
    void lockIsRequired() {
        repository.commit("main", model("АБС «Ядро»", ""));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");
        modelStates.state = new MemoryStates.State(modelStates.state.xml(), 3, false);

        ModelingDomainException e = assertThrows(ModelingDomainException.class,
                () -> imports.apply(ARCHITECT, model, confirm(preview, null)));

        assertEquals(ModelingCodes.LOCK_REQUIRED, e.code());
        assertEquals(List.of(1L, 2L, 3L), modelStates.versionNos());
        assertEquals(ModelingCodes.LOCK_REQUIRED,
                sessions.rows.values().iterator().next().findings().getFirst().code());
    }

    @Test
    @DisplayName("UC-IXC-006: дерево не собирается — в отчёте находка, применение — 422 и сессия REJECTED")
    void corruptTreeIsReportedAndRejected() {
        GitSha sha = repository.commitFiles("main", Map.of("model.yaml", "format_version: 9\n"));

        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");
        InterchangeException e = assertThrows(InterchangeException.class, () -> imports.apply(ARCHITECT, model,
                new GitImportService.PullRequest("main", sha.value(),
                        preview.baseHash(), null)));

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
                "nope")));
        assertEquals(InterchangeCodes.GIT_MODEL_NOT_IN_REF, code(() -> imports.preview(ARCHITECT, model,
                "other")));
        assertEquals(InterchangeCodes.INVALID_REQUEST, code(() -> imports.preview(ARCHITECT, model,
                "a..b")));
        assertEquals(InterchangeCodes.ACCESS_DENIED, code(() -> imports.preview(VIEWER, model, "main")));
        bindings.save(bindings.find(workspace).orElseThrow().disabled());
        assertEquals(InterchangeCodes.GIT_NOT_BOUND, code(() -> imports.preview(ARCHITECT, model,
                "main")));
    }

    @Test
    @DisplayName("FR-37: импорт головы ветки закрывает старые записи очереди и делает голову коммитом привязки")
    void importOfHeadSupersedesQueue() {
        outbox.enqueue(workspace, model, 2, Instant.EPOCH);
        outbox.enqueue(workspace, model, 3, Instant.EPOCH);
        GitSha head = repository.commit("main", model("АБС «Ядро»", NODE));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");

        imports.apply(ARCHITECT, model, confirm(preview, null));

        assertEquals(List.of("SKIPPED", "SKIPPED"), outbox.statuses());
        assertEquals(head, bindings.find(workspace).orElseThrow().lastCommitSha());
        assertTrue(versions.shas.isEmpty(), "новую версию признает публикация, а не импорт");
    }

    @Test
    @DisplayName("FR-37: голова с тем же содержимым — версии нет, последняя получает идентификатор головы")
    void unchangedHeadIsAdoptedByLastVersion() {
        outbox.enqueue(workspace, model, 3, Instant.EPOCH);
        GitSha head = repository.commit("main", model("АБС", ""));
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "main");

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
        GitImportService.Preview preview = imports.preview(ARCHITECT, model, "v1");

        imports.apply(ARCHITECT, model, confirm(preview, null));

        assertEquals(RefKind.TAG, preview.ref().kind());
        assertEquals(List.of("PENDING"), outbox.statuses());
    }

    // ── данные ─────────────────────────────────────────────────────────────

    private static final String NODE = "<element xsi:type=\"archimate:Node\" name=\"Сервер\" id=\"id-node\"/>";
    /** Доступ узла к компоненту приложения — матрица ArchiMate его не допускает. */
    private static final String FORBIDDEN = "<element xsi:type=\"archimate:AccessRelationship\" id=\"id-bad\" "
            + "source=\"id-node\" target=\"id-app\"/>";

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
        return new GitImportService.PullRequest(preview.ref().ref().value(),
                preview.ref().sha().value(), preview.baseHash(), comment);
    }

    private static String code(Runnable call) {
        return assertThrows(InterchangeException.class, call::run).code();
    }
}
