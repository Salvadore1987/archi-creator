package uz.salvadore.hamkorbank.archi.bootstrap.interchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.ImportReport;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.ImportService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.IllegalImportTransitionException;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.Severity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

class ImportIT extends IntegrationTest {

    @Autowired
    ImportService imports;
    @Autowired
    ImportSessionRepository sessions;

    @AfterEach
    void lenientAgain() {
        imports.configureStrictImport(ADMIN, workspace().value(), false);
    }

    @Test
    @DisplayName("INV-IXC-003: повтор с тем же ключом не создаёт вторую модель")
    void retryDoesNotCreateSecondModel() {
        byte[] file = Fixtures.fixture("styled_objects");
        int before = models();

        ImportReport first = imports.importFile(ARCHITECT, workspace().value(), "s.archimate", file,
                Optional.of("retry-1"));
        ImportReport retry = imports.importFile(ARCHITECT, workspace().value(), "s.archimate", file,
                Optional.of("retry-1"));

        assertEquals(first.modelId(), retry.modelId());
        assertTrue(retry.replayed());
        assertEquals(before + 1, models());
        InterchangeException otherFile = assertThrows(InterchangeException.class, () -> imports.importFile(
                ARCHITECT, workspace().value(), "x.archimate", Fixtures.fixture("nested_containment"),
                Optional.of("retry-1")));
        assertEquals(Failure.CONFLICT, otherFile.failure());
    }

    @Test
    @DisplayName("INV-IXC-002: применённая сессия повторно не применяется")
    void applyTwiceIsRejected() {
        ImportReport report = imports.importFile(ARCHITECT, workspace().value(), "a.archimate",
                Fixtures.fixture("nested_containment"), Optional.empty());
        ImportSession stored = sessions.find(report.sessionId()).orElseThrow();

        assertEquals(ImportStatus.APPLIED, stored.status());
        assertThrows(IllegalImportTransitionException.class,
                () -> stored.apply(new ModelId(UUID.randomUUID()), 1, 0, Instant.now()));
    }

    @Test
    @DisplayName("INV-IXC-007: повреждённый файл отклоняется в обоих режимах, модель не создаётся")
    void corruptedFileIsRejectedInBothModes() {
        byte[] corrupt = "<archimate:model xmlns:archimate=\"http://www.archimatetool.com/archimate\" id=\"id-1\">"
                .getBytes(StandardCharsets.UTF_8);
        int before = models();

        ImportReport lenient = imports.importFile(ARCHITECT, workspace().value(), "c.archimate", corrupt,
                Optional.empty());
        imports.configureStrictImport(ADMIN, workspace().value(), true);
        ImportReport strict = imports.importFile(ARCHITECT, workspace().value(), "c.archimate", corrupt,
                Optional.empty());

        assertEquals(ImportStatus.REJECTED, lenient.status());
        assertEquals(ImportStatus.REJECTED, strict.status());
        assertTrue(lenient.rejectedAsCorrupt() && strict.rejectedAsCorrupt());
        assertFalse(lenient.findings().isEmpty(), "отказ с объяснением, а не чёрный ящик");
        assertEquals(before, models());
    }

    @Test
    @DisplayName("FR-49: нарушение матрицы — отчёт в мягком режиме, отказ в строгом")
    void matrixViolationDependsOnStrictMode() {
        ImportReport lenient = imports.importFile(ARCHITECT, workspace().value(), "v.archimate",
                Fixtures.withMatrixViolation(), Optional.empty());
        imports.configureStrictImport(ADMIN, workspace().value(), true);
        ImportReport strict = imports.importFile(ARCHITECT, workspace().value(), "v.archimate",
                Fixtures.withMatrixViolation(), Optional.empty());

        assertEquals(ImportStatus.APPLIED, lenient.status());
        assertTrue(lenient.findings().stream().anyMatch(f -> f.severity() == Severity.ERROR
                && f.code().equals("RELATION_NOT_PERMITTED") && f.archiId().orElseThrow().value().equals("id-r1")));
        assertEquals(ImportStatus.REJECTED, strict.status());
        assertFalse(strict.rejectedAsCorrupt());
        assertTrue(strict.modelId().isEmpty());
    }

    @Test
    @DisplayName("FR-49, FR-28: строгость меняет только ADMIN, импорт — не VIEWER")
    void rolesGuardImport() {
        assertEquals(Failure.FORBIDDEN, assertThrows(InterchangeException.class,
                () -> imports.configureStrictImport(ARCHITECT, workspace().value(), true)).failure());
        assertEquals(Failure.FORBIDDEN, assertThrows(InterchangeException.class, () -> imports.importFile(VIEWER,
                workspace().value(), "v.archimate", Fixtures.fixture("styled_objects"), Optional.empty())).failure());
    }

    private int models() {
        return jdbc.queryForObject("select count(*) from model", Integer.class);
    }
}
