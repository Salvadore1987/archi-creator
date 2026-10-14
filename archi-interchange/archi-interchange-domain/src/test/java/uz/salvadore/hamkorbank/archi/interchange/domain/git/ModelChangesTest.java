package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges.Change;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges.Count;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges.Kind;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges.Type;

class ModelChangesTest {

    private static final String APP = """
            <element xsi:type="archimate:ApplicationComponent" name="АБС" id="id-app"/>""";
    private static final String CRM = """
            <element xsi:type="archimate:ApplicationComponent" name="CRM" id="id-crm"/>""";
    private static final String SERVING = """
            <element xsi:type="archimate:ServingRelationship" id="id-rel" source="id-app" target="id-crm"/>""";
    private static final String VIEW = """
            <element xsi:type="archimate:ArchimateDiagramModel" name="Контекст" id="id-view">
              <child xsi:type="archimate:DiagramObject" id="id-node" archimateElement="id-app">
                <bounds x="%d" y="24" width="120" height="55"/>
              </child>
            </element>""";

    @Test
    @DisplayName("FR-38: одинаковые состояния — отчёт пуст, сводка из нулей по каждому виду")
    void sameStateHasNoChanges() {
        ModelDocument document = model(APP + CRM, SERVING, VIEW.formatted(12));

        ModelChanges changes = ModelChanges.between(document, document);

        assertTrue(changes.isEmpty());
        assertEquals(Kind.values().length, changes.summary().size());
        assertTrue(changes.summary().stream().allMatch(c -> c.added() == 0 && c.removed() == 0 && c.changed() == 0));
    }

    @Test
    @DisplayName("FR-38: добавленное, удалённое и изменённое — по идентификатору, с полями")
    void objectsAreMatchedById() {
        ModelDocument before = model(APP + CRM, SERVING, "");
        ModelDocument after = model(APP.replace("АБС", "АБС «Ядро»") + """
                <element xsi:type="archimate:Node" name="Сервер" id="id-node-1">
                  <documentation>стойка 4</documentation>
                </element>""", "", "");

        ModelChanges changes = ModelChanges.between(before, after);

        assertEquals(List.of(
                new Change(Kind.ELEMENT, Type.ADDED, id("id-node-1"), Optional.of("Node"), "Сервер", List.of()),
                new Change(Kind.ELEMENT, Type.REMOVED, id("id-crm"), Optional.of("ApplicationComponent"), "CRM",
                        List.of()),
                new Change(Kind.ELEMENT, Type.CHANGED, id("id-app"), Optional.of("ApplicationComponent"),
                        "АБС «Ядро»", List.of("name")),
                new Change(Kind.RELATIONSHIP, Type.REMOVED, id("id-rel"), Optional.of("ServingRelationship"), "",
                        List.of())), changes.changes());
        assertTrue(changes.summary().containsAll(List.of(new Count(Kind.ELEMENT, 1, 1, 1),
                new Count(Kind.RELATIONSHIP, 0, 1, 0))));
    }

    @Test
    @DisplayName("FR-38: перенос в другую папку — поле folder, а не удаление с добавлением")
    void moveIsFolderChange() {
        String other = "<folder name=\"Прочее\" id=\"id-other\"/>";
        ModelDocument before = document("""
                <folder name="Приложения" id="id-apps" type="application">%s</folder>%s""".formatted(APP, other));
        ModelDocument after = document("""
                <folder name="Приложения" id="id-apps" type="application"/>
                <folder name="Прочее" id="id-other">%s</folder>""".formatted(APP));

        Change change = single(ModelChanges.between(before, after));
        assertEquals(id("id-app"), change.archiId());
        assertEquals(List.of(ModelChanges.FOLDER), change.fields());
    }

    @Test
    @DisplayName("FR-38: тип — type без префикса, свойства и описание — properties и documentation")
    void valuesAndTypeAreFields() {
        ModelDocument before = model("""
                <element xsi:type="archimate:ApplicationComponent" name="АБС" id="id-app">
                  <property key="owner" value="FIDO"/>
                </element>""", "", "");
        ModelDocument after = model("""
                <element xsi:type="archimate:ApplicationCollaboration" name="АБС" id="id-app">
                  <documentation>ядро</documentation>
                  <property key="owner" value="ИТ"/>
                </element>""", "", "");

        Change change = ModelChanges.between(before, after).changes().getFirst();

        assertEquals(Optional.of("ApplicationCollaboration"), change.archiType());
        assertEquals(List.of(ModelChanges.TYPE, ModelChanges.PROPERTIES, ModelChanges.DOCUMENTATION),
                change.fields());
    }

    @Test
    @DisplayName("FR-38: правка на представлении — одно поле diagram у представления, а не строки по узлам")
    void diagramEditsRollUpIntoView() {
        ModelDocument before = model(APP, "", VIEW.formatted(12));
        ModelDocument after = model(APP, "", VIEW.formatted(240));

        assertEquals(List.of(new Change(Kind.VIEW, Type.CHANGED, id("id-view"),
                        Optional.of("ArchimateDiagramModel"), "Контекст", List.of(ModelChanges.DIAGRAM))),
                ModelChanges.between(before, after).changes());
    }

    @Test
    @DisplayName("FR-38: вставка соседа порядок не меняет, перестановка — поле order у папки")
    void orderIsRelativeOrderOfCommonChildren() {
        ModelDocument before = model(APP + CRM, "", "");
        ModelDocument inserted = model(APP + "<element xsi:type=\"archimate:Node\" name=\"N\" id=\"id-n\"/>" + CRM,
                "", "");
        ModelDocument swapped = model(CRM + APP, "", "");

        assertEquals(List.of(Type.ADDED),
                ModelChanges.between(before, inserted).changes().stream().map(Change::type).toList());
        Change change = single(ModelChanges.between(before, swapped));
        assertEquals(Kind.FOLDER, change.kind());
        assertEquals(List.of(ModelChanges.ORDER), change.fields());
    }

    @Test
    @DisplayName("FR-38: имя и описание модели — строка вида MODEL")
    void modelAttributesAreReported() {
        ModelDocument before = document("");
        ModelDocument after = read(xml("").replace("name=\"Тест\"", "name=\"Тест 2\"")
                .replace("version=\"5.0.0\">", "version=\"5.0.0\">\n<purpose>ландшафт</purpose>"));

        Change change = single(ModelChanges.between(before, after));
        assertEquals(Kind.MODEL, change.kind());
        assertEquals("Тест 2", change.name());
        assertEquals(List.of("name", ModelChanges.DOCUMENTATION), change.fields());
    }

    private static Change single(ModelChanges changes) {
        assertEquals(1, changes.changes().size(), changes.changes()::toString);
        return changes.changes().getFirst();
    }

    private static ModelDocument model(String elements, String relations, String views) {
        return document("""
                <folder name="Приложения" id="id-apps" type="application">%s</folder>
                <folder name="Связи" id="id-rels" type="relations">%s</folder>
                <folder name="Представления" id="id-views" type="diagrams">%s</folder>"""
                .formatted(elements, relations, views));
    }

    private static ModelDocument document(String content) {
        return read(xml(content));
    }

    private static String xml(String content) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Тест" id="id-model" version="5.0.0">
                %s
                </archimate:model>
                """.formatted(content);
    }

    private static ModelDocument read(String xml) {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId id(String value) {
        return uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId.of(value);
    }
}
