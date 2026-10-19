package uz.salvadore.hamkorbank.archi.bootstrap.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefSchema;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;

/**
 * Эталоны выгрузки в Open Exchange Format: {@code oef_export_<фикстура>.xml} — файл,
 * который сервер печатает из фикстуры с настоящей нотацией, и
 * {@code oef_export_<фикстура>.losses.txt} — отчёт о потерях по строке на запись
 * ({@code archiId вид аспект}). Эталон валиден по официальной схеме; расхождение
 * писателя с эталоном роняет сборку, и это видно в диффе по тексту.
 *
 * <p>Пересъёмка: {@code ./mvnw -P '!frontend' -pl archi-bootstrap -am test
 * -Dtest=OefExportFixturesTest -Doef.fixtures.update=true -Dsurefire.failIfNoSpecifiedTests=false}.
 */
class OefExportFixturesTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
    private static final OefSchema SCHEMA = OefSchema.load();
    private static final OefWriter WRITER = new OefWriter(SharedNotation.load(InterTypesetter.FAMILY));

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"unknown_extension", "styled_objects", "nested_containment", "capability_and_location"})
    @DisplayName("FR-43, FR-44: выгрузка фикстуры совпадает с эталоном, эталон валиден по XSD, "
            + "отчёт — с ожидаемыми позициями")
    void exportMatchesFixture(String name) throws IOException {
        OefWriter.Written written = WRITER.write(new StaxArchiDocumentReader()
                .read(new ByteArrayInputStream(Fixtures.fixture(name))));
        String losses = written.losses().stream()
                .map(l -> l.archiId() + " " + l.objectKind().value() + " " + l.aspect())
                .collect(Collectors.joining("\n", "", "\n"));
        Path xml = FIXTURES.resolve("oef_export_" + name + ".xml");
        Path report = FIXTURES.resolve("oef_export_" + name + ".losses.txt");
        if (Boolean.getBoolean("oef.fixtures.update")) {
            Files.write(xml, written.xml());
            Files.writeString(report, losses, StandardCharsets.UTF_8);
        }
        byte[] expected = Files.readAllBytes(xml);
        assertThat(SCHEMA.violation(expected)).as("эталон %s валиден по XSD", name).isEmpty();
        assertThat(new String(written.xml(), StandardCharsets.UTF_8)).as("эталон %s — переснять", name)
                .isEqualTo(new String(expected, StandardCharsets.UTF_8));
        assertThat(losses).as("отчёт %s — переснять", name).isEqualTo(Files.readString(report, StandardCharsets.UTF_8));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"unknown_extension"})
    @DisplayName("INV-IXC-006: остаток XML фикстуры — в эталонном отчёте")
    void rawFragmentsAreInFixtureReport(String name) throws IOException {
        List<String> lines = Files.readAllLines(FIXTURES.resolve("oef_export_" + name + ".losses.txt"));
        assertThat(lines).contains("id-53f986115270259ff902bf66cf3dca2c element xml:vendor:lineage",
                "id-054b740bdf40fe499e06add25ef7a09f model xml:vendor:metadata",
                "id-3a983a300ab35ca41745f730633ff872 view type");
    }
}
