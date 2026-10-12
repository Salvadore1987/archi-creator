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
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.StyleOverride;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Раскладка без базы: документ → строки модели с остатком → документ. Сравнивается
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

    @Test
    @DisplayName("ADR-0017: подпись группы и текст заметки — в полях узла, а не в остатке; сборка их возвращает")
    void groupLabelAndNoteContentAreTyped() throws IOException {
        ModelContent styled = decompose(read(Files.readAllBytes(
                ROOT.resolve("archi-bootstrap/src/test/resources/fixtures/styled_objects.archimate"))));
        ViewNode note = styled.views().stream().flatMap(v -> v.nodes().values().stream())
                .filter(n -> n.kind() == ViewNodeKind.NOTE).findFirst().orElseThrow();
        assertEquals(Optional.of("Легенда:\nкрасное — платёжный поток;\nзелёное — обслуживание клиента."),
                note.content());
        assertTrue(note.rawXml().orElseThrow().value().indexOf("Легенда") < 0, "текста заметки нет в остатке");

        ModelContent reference = decompose(read(Files.readAllBytes(ROOT.resolve("docs/Hamkorbank_AS_IS_strict.archimate"))));
        ViewNode group = reference.views().stream().flatMap(v -> v.nodes().values().stream())
                .filter(n -> n.kind() == ViewNodeKind.GROUP && n.label().equals(Optional.of("Каналы")))
                .findFirst().orElseThrow();
        assertTrue(group.rawXml().orElseThrow().value().indexOf("Каналы") < 0, "подписи группы нет в остатке");

        View view = styled.views().stream().filter(v -> v.nodes().contains(note.id())).findFirst().orElseThrow();
        view.nodes().put(note.id(), new ViewNode(note.id(), note.parentId(), note.archiId(), note.archiType(),
                note.elementId(), note.bounds(), note.style(), note.label(), Optional.of("Первая\r\nвторая"),
                note.sortOrder(), note.rawXml()));
        String xml = new String(write(new DocumentAssembler().assemble(styled)), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<content>Первая&#xD;\nвторая</content>"), "столбец сильнее остатка, CR не теряется");
        ModelContent again = decompose(read(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.of("Первая\r\nвторая"), again.views().stream()
                .flatMap(v -> v.nodes().values().stream()).filter(n -> n.archiId().equals(note.archiId()))
                .findFirst().orElseThrow().content());
    }

    @Test
    @DisplayName("ADR-0017: новая группа и заметка пишутся в порядке Archi — name после id, content после bounds")
    void newGroupAndNoteFollowArchiLayout() throws IOException {
        ModelContent content = decompose(read(Files.readAllBytes(
                ROOT.resolve("archi-bootstrap/src/test/resources/fixtures/styled_objects.archimate"))));
        View view = content.views().getFirst();
        ArchiIdGenerator archiIds = new ArchiIdGenerator();
        ViewNode group = new ViewNode(ViewNodeId.of(UUID.randomUUID()), Optional.empty(), archiIds.next(),
                DiagramType.of("archimate:Group"), Optional.empty(), new Bounds(0, 0, 400, 300), StyleOverride.NONE,
                Optional.of("Каналы"), Optional.empty(), SortOrder.of(900_000), Optional.empty());
        ViewNode note = new ViewNode(ViewNodeId.of(UUID.randomUUID()), Optional.empty(), archiIds.next(),
                DiagramType.of("archimate:Note"), Optional.empty(), new Bounds(10, 10, 185, 80), StyleOverride.NONE,
                Optional.empty(), Optional.of("строка 1\nстрока 2"), SortOrder.of(901_000), Optional.empty());
        view.nodes().put(group.id(), group);
        view.nodes().put(note.id(), note);

        String xml = new String(write(new DocumentAssembler().assemble(content)), StandardCharsets.UTF_8);

        assertTrue(xml.contains("<child xsi:type=\"archimate:Group\" id=\"" + group.archiId() + "\" name=\"Каналы\">"),
                xml);
        assertTrue(xml.contains("<child xsi:type=\"archimate:Note\" id=\"" + note.archiId() + "\">\n"), xml);
        int bounds = xml.indexOf("<bounds x=\"10\" y=\"10\" width=\"185\" height=\"80\"/>");
        int text = xml.indexOf("<content>строка 1\nстрока 2</content>");
        assertTrue(bounds > 0 && text > bounds, "текст заметки — после геометрии");
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
