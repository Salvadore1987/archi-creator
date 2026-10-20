package uz.salvadore.hamkorbank.archi.bootstrap.interchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.XmlEquivalence.assertXmlEquivalent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.beans.factory.annotation.Autowired;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ImportArchimateFileCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportModelQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse.Outcome;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ExportApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ImportApplicationService;

/**
 * Круг без потерь на уровне сценариев: импорт → таблицы → экспорт даёт
 * исходный файл без потерь. Тот же круг через REST — {@code RoundTripApiIT}.
 */
class ModelImportIT extends IntegrationTest {

    static final List<String> FIXTURES = Fixtures.SYNTHETIC;

    @Autowired
    ImportApplicationService imports;
    @Autowired
    ExportApplicationService exports;

    @Test
    @DisplayName("INV-MDL-009, INV-IXC-005: эталон проходит импорт → таблицы → экспорт без потерь")
    void folderTreeSurvivesRoundTrip() {
        byte[] original = Fixtures.reference();

        ImportArchimateFileResponse report = importFile("Hamkorbank.archimate", original);

        assertEquals(Outcome.APPLIED, report.outcome(), () -> report.findings().toString());
        UUID model = report.modelId();
        assertEquals(1L, report.versionNo());
        assertEquals(402, count("element where model_id = ?", model));
        assertEquals(12, count("view where model_id = ?", model));
        assertXmlEquivalent(original, archimate(model, null));
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("FIXTURES")
    @DisplayName("FR-03, INV-IXC-005: фикстура §9.1 проходит импорт → таблицы → экспорт без потерь")
    void fixtureSurvivesDatabaseRoundTrip(String fixture) {
        byte[] original = Fixtures.fixture(fixture);

        ImportArchimateFileResponse report = importFile(fixture + ".archimate", original);

        assertEquals(Outcome.APPLIED, report.outcome(), () -> report.findings().toString());
        assertXmlEquivalent(original,
                archimate(report.modelId(), 1L));
    }

    @Test
    @DisplayName("NFR-02: импорт эталонной модели укладывается в 3 секунды")
    void referenceImportsWithinThreeSeconds() {
        byte[] original = Fixtures.reference();
        importFile("warm-up.archimate", original);

        Instant start = Instant.now();
        importFile("timed.archimate", original);
        Duration took = Duration.between(start, Instant.now());

        assertTrue(took.compareTo(Duration.ofSeconds(3)) <= 0, "импорт занял " + took);
    }

    private ImportArchimateFileResponse importFile(String name, byte[] file) {
        return imports.importArchimateFile(ARCHITECT,
                new ImportArchimateFileCommand(workspace().value(), name, file, null));
    }

    private byte[] archimate(UUID model, Long versionNo) {
        return exports.exportModel(VIEWER, new ExportModelQuery(model, null, versionNo, null, null)).content();
    }

    private int count(String fromWhere, UUID model) {
        return jdbc.queryForObject("select count(*) from " + fromWhere, Integer.class, model);
    }
}
