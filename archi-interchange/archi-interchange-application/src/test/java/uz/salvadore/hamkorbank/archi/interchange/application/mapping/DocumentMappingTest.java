package uz.salvadore.hamkorbank.archi.interchange.application.mapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiIdGenerator;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * ADR-0017 без базы: документ → строки модели с остатком → документ. Сравнивается
 * не XML, а сам документ как значение — со строгим порядком атрибутов и содержимого,
 * то есть строже {@code assertXmlEquivalent}. Через таблицы тот же путь проверяет
 * {@code ModelImportIT}.
 */
class DocumentMappingTest {

    private static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    static Stream<Path> sources() throws IOException {
        Path fixtures = ROOT.resolve("archi-bootstrap/src/test/resources/fixtures");
        try (Stream<Path> files = Files.list(fixtures)) {
            return Stream.concat(Stream.of(ROOT.resolve("docs/Hamkorbank_AS_IS_strict.archimate")),
                    files.filter(f -> f.toString().endsWith(".archimate")).sorted().toList().stream());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    @DisplayName("INV-IXC-005: раскладка по строкам и сборка обратно дают тот же документ")
    void documentSurvivesDecomposition(Path source) throws IOException {
        ModelDocument original = read(Files.readAllBytes(source));

        ModelContent content = decompose(original);
        ModelDocument assembled = new DocumentAssembler().assemble(content);

        assertEquals(original, assembled);
        assertArrayEquals(write(original), write(assembled), "те же байты писателя");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    @DisplayName("FR-03: незнакомое уходит в остаток, а не теряется")
    void unknownGoesToResidue(Path source) throws IOException {
        ModelContent content = decompose(read(Files.readAllBytes(source)));

        assertTrue(content.model().elements().values().stream().allMatch(e -> e.rawXml().isPresent()),
                "у импортированного элемента остаток есть всегда — в нём порядок атрибутов");
        assertEquals(9, content.model().roots().size());
    }

    static ModelContent decompose(ModelDocument document) {
        ArchiIdGenerator archiIds = new ArchiIdGenerator();
        return new DocumentDecomposer(UUID::randomUUID, archiIds::next).decompose(document,
                new DocumentDecomposer.Identity(ModelId.of(UUID.randomUUID()), WorkspaceId.of(UUID.randomUUID()),
                        "architect", NOW, NOW, 0));
    }

    static ModelDocument read(byte[] xml) {
        try (InputStream in = new java.io.ByteArrayInputStream(xml)) {
            return new StaxArchiDocumentReader().read(in);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static byte[] write(ModelDocument document) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ArchiXmlWriter().write(document, out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("ADR-0017: столбец сильнее остатка — правки видны в документе, новое встаёт как у Archi")
    void editsOverrideResidue() throws IOException {
        ModelContent content = decompose(read(Files.readAllBytes(
                ROOT.resolve("archi-bootstrap/src/test/resources/fixtures/unknown_extension.archimate"))));
        ArchitectureModel model = content.model();
        Element client = model.elements().values().stream().filter(e -> e.name().equals("Клиент"))
                .findFirst().orElseThrow();
        model.updateElement(client.id(), "Клиент банка", Optional.of("Физическое лицо"),
                List.of(new PropertyEntry("source", "CRM", SortOrder.of(1000)),
                        new PropertyEntry("owner", "Розница", SortOrder.of(2000))), NOW);
        model.addElement(ArchiType.ofSimpleName("BusinessRole"), "Плательщик", client.folderId(),
                ElementId.of(UUID.randomUUID()), ArchiId.of("id-new"), NOW);

        String xml = new String(write(new DocumentAssembler().assemble(content)), StandardCharsets.UTF_8);

        assertTrue(xml.contains("""
                    <element xsi:type="archimate:BusinessActor" name="Клиент банка" \
                id="id-53f986115270259ff902bf66cf3dca2c" vendor:owner="Розница">
                      <documentation>Физическое лицо</documentation>
                      <property key="source" value="CRM"/>
                      <property key="owner" value="Розница"/>
                      <vendor:lineage system="CRM" table="clients">"""), xml);
        assertTrue(xml.contains("    <element xsi:type=\"archimate:BusinessRole\" name=\"Плательщик\" id=\"id-new\"/>\n"
                + "  </folder>"), xml);
    }
}
