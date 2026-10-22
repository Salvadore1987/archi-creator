package uz.salvadore.hamkorbank.archi.bootstrap.interchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureImportPolicyCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ImportArchimateFileCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetImportPolicyQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse.Outcome;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ImportApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ImportPolicyApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.IllegalImportTransitionException;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportRequest;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.Severity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

class ImportIT extends IntegrationTest {

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Autowired
    ImportApplicationService imports;
    @Autowired
    ImportPolicyApplicationService policies;
    @Autowired
    ImportSessionRepository sessions;
    @Autowired
    PlatformTransactionManager transactions;

    @AfterEach
    void lenientAgain() {
        strict(ADMIN, false);
    }

    @Test
    @DisplayName("INV-IXC-003: повтор с тем же ключом не создаёт вторую модель")
    void retryDoesNotCreateSecondModel() {
        byte[] file = Fixtures.fixture("styled_objects");
        int before = models();

        ImportArchimateFileResponse first = importFile(ARCHITECT, workspace().value(), "s.archimate", file,
                "retry-1");
        ImportArchimateFileResponse retry = importFile(ARCHITECT, workspace().value(), "s.archimate", file,
                "retry-1");

        assertEquals(first.modelId(), retry.modelId());
        assertEquals(Outcome.REPLAYED, retry.outcome());
        assertEquals(before + 1, models());
        InterchangeException otherFile = assertThrows(InterchangeException.class, () -> importFile(
                ARCHITECT, workspace().value(), "x.archimate", Fixtures.fixture("nested_containment"),
                "retry-1"));
        assertEquals(Failure.CONFLICT, otherFile.failure());
    }

    @Test
    @DisplayName("INV-IXC-003: повторная запись сессии в той же транзакции не дублирует находки и не отказывает")
    void savingSessionTwiceInOneTransactionKeepsFindings() {
        ImportSessionId id = new ImportSessionId(IDS.generate());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            ImportSession session = ImportSession.receive(id, ImportRequest.of(new WorkspaceId(workspace().value()),
                    null, "twice.archimate", new byte[] {1}, "twice-" + id.value(), false, "architect-it"),
                    clock.instant());
            session.reject(List.of(new ImportFinding(new FindingId(IDS.generate()), Severity.ERROR, "IXC_TEST",
                    Message.of(InterchangeMessages.IMPORT_IN_PROGRESS), null, null)), clock.instant());
            sessions.save(session);
            sessions.save(session);
        });

        assertEquals(1, sessions.find(id).orElseThrow().findings().size());
    }

    @Test
    @DisplayName("INV-IXC-002: применённая сессия повторно не применяется")
    void applyTwiceIsRejected() {
        ImportArchimateFileResponse report = importFile(ARCHITECT, workspace().value(), "a.archimate",
                Fixtures.fixture("nested_containment"), null);
        ImportSession stored = sessions.find(new ImportSessionId(report.sessionId())).orElseThrow();

        assertEquals(ImportStatus.APPLIED, stored.status());
        assertThrows(IllegalImportTransitionException.class,
                () -> stored.apply(new ModelId(IDS.generate()), 1, 0, clock.instant()));
    }

    @Test
    @DisplayName("INV-IXC-007: повреждённый файл отклоняется в обоих режимах, модель не создаётся")
    void corruptedFileIsRejectedInBothModes() {
        byte[] corrupt = "<archimate:model xmlns:archimate=\"http://www.archimatetool.com/archimate\" id=\"id-1\">"
                .getBytes(StandardCharsets.UTF_8);
        int before = models();

        ImportArchimateFileResponse lenient = importFile(ARCHITECT, workspace().value(), "c.archimate", corrupt,
                null);
        strict(ADMIN, true);
        ImportArchimateFileResponse strict = importFile(ARCHITECT, workspace().value(), "c.archimate", corrupt,
                null);

        assertEquals(Outcome.REJECTED_CORRUPT, lenient.outcome());
        assertEquals(Outcome.REJECTED_CORRUPT, strict.outcome());
        assertFalse(lenient.findings().isEmpty(), "отказ с объяснением, а не чёрный ящик");
        assertEquals(before, models());
    }

    @Test
    @DisplayName("FR-49: нарушение матрицы — отчёт в мягком режиме, отказ в строгом")
    void matrixViolationDependsOnStrictMode() {
        ImportArchimateFileResponse lenient = importFile(ARCHITECT, workspace().value(), "v.archimate",
                Fixtures.withMatrixViolation(), null);
        strict(ADMIN, true);
        ImportArchimateFileResponse strict = importFile(ARCHITECT, workspace().value(), "v.archimate",
                Fixtures.withMatrixViolation(), null);

        assertEquals(Outcome.APPLIED, lenient.outcome());
        assertTrue(lenient.findings().stream().anyMatch(f -> f.severity().equals("ERROR")
                && f.code().equals("RELATION_NOT_PERMITTED") && f.archiId().equals("id-r1")));
        assertEquals(Outcome.REJECTED_STRICT, strict.outcome());
        assertNull(strict.modelId());
    }

    @Test
    @DisplayName("FR-49: смена строгости отдаёт итоговую настройку, чтение — ту же")
    void configuredPolicyIsReturnedAndRead() {
        assertTrue(strict(ADMIN, true));
        assertTrue(policies.getImportPolicy(ARCHITECT, new GetImportPolicyQuery(workspace().value())).strictImport());
    }

    @Test
    @DisplayName("FR-49, FR-28: строгость меняет только ADMIN, читает не VIEWER, импорт — не VIEWER")
    void rolesGuardImport() {
        assertEquals(Failure.FORBIDDEN, assertThrows(InterchangeException.class,
                () -> policies.getImportPolicy(VIEWER, new GetImportPolicyQuery(workspace().value()))).failure());
        assertEquals(Failure.FORBIDDEN, assertThrows(InterchangeException.class,
                () -> strict(ARCHITECT, true)).failure());
        assertEquals(Failure.FORBIDDEN, assertThrows(InterchangeException.class, () -> importFile(VIEWER,
                workspace().value(), "v.archimate", Fixtures.fixture("styled_objects"), null)).failure());
    }

    private ImportArchimateFileResponse importFile(EditorIdentity actor, UUID workspaceId, String name, byte[] file,
                                                   String key) {
        return imports.importArchimateFile(actor, new ImportArchimateFileCommand(workspaceId, name, file, key));
    }

    private boolean strict(EditorIdentity actor, boolean strict) {
        return policies.configureImportPolicy(actor, new ConfigureImportPolicyCommand(workspace().value(), strict))
                .strictImport();
    }

    private int models() {
        return jdbc.queryForObject("select count(*) from model", Integer.class);
    }
}
