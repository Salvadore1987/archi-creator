package uz.salvadore.hamkorbank.archi.bootstrap.interchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.XmlEquivalence.assertXmlEquivalent;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.beans.factory.annotation.Autowired;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportService;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.ImportReport;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.ImportService;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;

/**
 * Критерий готовности этапа 2 на уровне сценариев: импорт → таблицы → экспорт даёт
 * исходный файл без потерь. Тот же круг через REST — {@code RoundTripApiIT}.
 */
class ModelImportIT extends IntegrationTest {

    static final java.util.List<String> FIXTURES = Fixtures.SYNTHETIC;

    @Autowired
    ImportService imports;
    @Autowired
    ExportService exports;

    @Test
    @DisplayName("INV-MDL-009, INV-IXC-005: эталон проходит импорт → таблицы → экспорт без потерь")
    void folderTreeSurvivesRoundTrip() {
        byte[] original = Fixtures.reference();

        ImportReport report = imports.importFile(ARCHITECT, workspace().value(), "Hamkorbank.archimate", original,
                Optional.empty());

        assertEquals(ImportStatus.APPLIED, report.status(), () -> report.findings().toString());
        UUID model = report.modelId().orElseThrow();
        assertEquals(1, report.versionNo());
        assertEquals(402, count("element where model_id = ?", model));
        assertEquals(12, count("view where model_id = ?", model));
        assertXmlEquivalent(original, exports.archimate(VIEWER, model, Optional.empty()).artifact().bytes());
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("FIXTURES")
    @DisplayName("FR-03, INV-IXC-005: фикстура §9.1 проходит импорт → таблицы → экспорт без потерь")
    void fixtureSurvivesDatabaseRoundTrip(String fixture) {
        byte[] original = Fixtures.fixture(fixture);

        ImportReport report = imports.importFile(ARCHITECT, workspace().value(), fixture + ".archimate", original,
                Optional.empty());

        assertEquals(ImportStatus.APPLIED, report.status(), () -> report.findings().toString());
        assertXmlEquivalent(original,
                exports.archimate(VIEWER, report.modelId().orElseThrow(), Optional.of(1L)).artifact().bytes());
    }

    @Test
    @DisplayName("NFR-02: импорт эталонной модели укладывается в 3 секунды")
    void referenceImportsWithinThreeSeconds() {
        byte[] original = Fixtures.reference();
        imports.importFile(ARCHITECT, workspace().value(), "warm-up.archimate", original, Optional.empty());

        Instant start = Instant.now();
        imports.importFile(ARCHITECT, workspace().value(), "timed.archimate", original, Optional.empty());
        Duration took = Duration.between(start, Instant.now());

        assertTrue(took.compareTo(Duration.ofSeconds(3)) <= 0, "импорт занял " + took);
    }

    private int count(String fromWhere, UUID model) {
        return jdbc.queryForObject("select count(*) from " + fromWhere, Integer.class, model);
    }
}
