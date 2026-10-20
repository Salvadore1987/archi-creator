package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.EntityMerge.Cascade;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.EntityMerge.CascadeKind;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.EntityMerge.Conflict;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.EntityMerge.Reason;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.EntityMerge.Result;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.EntityMerge.Side;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges.Kind;

class EntityMergeTest {

    private static final Path REFERENCE = Path.of("../../docs/Hamkorbank_AS_IS_strict.archimate");

    private static final String APP = """
            <element xsi:type="archimate:ApplicationComponent" name="АБС" id="id-app"/>""";
    private static final String CRM = """
            <element xsi:type="archimate:ApplicationComponent" name="CRM" id="id-crm"/>""";
    private static final String SERVING = """
            <element xsi:type="archimate:ServingRelationship" id="id-rel" source="id-app" target="id-crm"/>""";
    private static final String VIEW = """
            <element xsi:type="archimate:ArchimateDiagramModel" name="Контекст" id="id-view">
              <child xsi:type="archimate:DiagramObject" id="id-node-app" archimateElement="id-app">
                <bounds x="%d" y="24" width="120" height="55"/>
              </child>%s
            </element>""";
    private static final String CRM_NODE = """
            <child xsi:type="archimate:DiagramObject" id="id-node-crm" archimateElement="id-crm">
              <bounds x="300" y="24" width="120" height="55"/>
            </child>""";

    @Test
    @DisplayName("INV-IXC-010: непересекающиеся правки сливаются без конфликтов")
    void disjointEditsMergeWithoutConflict() {
        ModelDocument base = model(APP + CRM, SERVING, VIEW.formatted(12, ""));
        ModelDocument ours = model(APP.replace("АБС", "АБС «Ядро»") + CRM, SERVING, VIEW.formatted(12, ""));
        ModelDocument theirs = model(APP + CRM.replace("/>", "><documentation>учёт клиентов</documentation></element>")
                + "<element xsi:type=\"archimate:Node\" name=\"Сервер\" id=\"id-srv\"/>", SERVING,
                VIEW.formatted(40, ""));

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        assertEquals(List.of(), result.conflicts());
        assertTrue(result.resolved());
        ModelDocument merged = result.document();
        assertEquals(Optional.of("АБС «Ядро»"), node(merged, "id-app").attribute("name"));
        assertEquals(Optional.of("учёт клиентов"), node(merged, "id-crm").documentation());
        assertTrue(merged.find(ArchiId.of("id-srv")).isPresent());
        assertEquals(Optional.of("40"), node(merged, "id-node-app").values("bounds").getFirst().attribute("x"));
    }

    @Test
    @DisplayName("INV-IXC-010: одно поле изменено в обеих ветвях по-разному — конфликт по полю")
    void sameFieldEditsProduceFieldLevelConflict() {
        ModelDocument base = model(APP, "", "");
        ModelDocument ours = model(APP.replace("АБС", "АБС ours") .replace("/>",
                "><documentation>наше описание</documentation></element>"), "", "");
        ModelDocument theirs = model(APP.replace("АБС", "АБС theirs"), "", "");

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        Conflict conflict = single(result.conflicts());
        assertEquals("id-app:name", conflict.id());
        assertEquals(Kind.ELEMENT, conflict.kind());
        assertEquals("name", conflict.field());
        assertEquals("АБС", conflict.base());
        assertEquals("АБС ours", conflict.ours());
        assertEquals("АБС theirs", conflict.theirs());
        assertFalse(result.resolved());
        assertEquals(Optional.of("АБС ours"), node(result.document(), "id-app").attribute("name"));

        Result chosen = EntityMerge.merge(base, ours, theirs, Map.of("id-app:name", Side.THEIRS));

        assertTrue(chosen.resolved());
        assertEquals(Optional.of("АБС theirs"), node(chosen.document(), "id-app").attribute("name"));
        assertEquals(Optional.of("наше описание"), node(chosen.document(), "id-app").documentation(),
                "соседнее поле без конфликта берётся из своей ветви");
    }

    @Test
    @DisplayName("INV-IXC-010: переименование и перенос в другую папку не конфликтуют с правкой других полей")
    void renameAndMoveDoNotConflict() {
        String other = "<folder name=\"Прочее\" id=\"id-other\" type=\"other\"/>";
        ModelDocument base = document(folder(APP) + other);
        ModelDocument ours = document(folder("")
                + "<folder name=\"Прочее\" id=\"id-other\" type=\"other\">" + APP + "</folder>");
        ModelDocument theirs = document(folder(APP.replace("АБС", "АБС 2")) + other);

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        assertEquals(List.of(), result.conflicts());
        DocumentNode moved = result.document().folders().get(1).children().getFirst();
        assertEquals(ArchiId.of("id-app"), moved.archiId());
        assertEquals(Optional.of("АБС 2"), moved.attribute("name"));
    }

    @Test
    @DisplayName("INV-IXC-010: удалён в одной ветви, изменён в другой — явный выбор «удалить» или «оставить»")
    void removedVersusChangedIsExplicitChoice() {
        ModelDocument base = model(APP + CRM, "", "");
        ModelDocument ours = model(APP, "", "");
        ModelDocument theirs = model(APP + CRM.replace("CRM", "CRM 2"), "", "");

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        Conflict conflict = single(result.conflicts());
        assertEquals("id-crm:" + EntityMerge.REMOVAL, conflict.id());
        assertNull(conflict.ours());
        assertEquals("name", conflict.theirs());
        assertTrue(result.document().find(ArchiId.of("id-crm")).isEmpty(), "без выбора — «наше»: удалено");

        Result kept = EntityMerge.merge(base, ours, theirs, Map.of(conflict.id(), Side.THEIRS));
        assertEquals(Optional.of("CRM 2"), node(kept.document(), "id-crm").attribute("name"));
    }

    @Test
    @DisplayName("INV-IXC-010: удалённый элемент уносит связи другой ветви, они попадают в отчёт")
    void relationsGoWithRemovedElement() {
        ModelDocument base = model(APP + CRM, "", "");
        ModelDocument ours = model(APP, "", "");
        ModelDocument theirs = model(APP + CRM, SERVING, "");

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        assertEquals(List.of(), result.conflicts());
        assertTrue(result.document().find(ArchiId.of("id-rel")).isEmpty());
        assertEquals(List.of(new Cascade(CascadeKind.RELATIONSHIP, ArchiId.of("id-rel"), "",
                Reason.RELATION_ENDPOINT_REMOVED, null)), result.cascade());
    }

    @Test
    @DisplayName("INV-IXC-010: узел представления удалённого элемента уходит каскадом, targetConnections чистится")
    void diagramNodeOfRemovedElementGoes() {
        String connected = """
                <element xsi:type="archimate:ArchimateDiagramModel" name="Контекст" id="id-view">
                  <child xsi:type="archimate:DiagramObject" id="id-node-app" targetConnections="id-conn" archimateElement="id-app">
                    <bounds x="12" y="24" width="120" height="55"/>
                  </child>
                  <child xsi:type="archimate:DiagramObject" id="id-node-crm" archimateElement="id-crm">
                    <bounds x="300" y="24" width="120" height="55"/>
                    <sourceConnection xsi:type="archimate:Connection" id="id-conn" source="id-node-crm" target="id-node-app" archimateRelationship="id-rel2"/>
                  </child>
                </element>""";
        String reverse = SERVING.replace("id-rel\"", "id-rel2\"").replace("source=\"id-app\" target=\"id-crm\"",
                "source=\"id-crm\" target=\"id-app\"");
        ModelDocument base = model(APP + CRM, "", VIEW.formatted(12, ""));
        ModelDocument ours = model(APP, "", VIEW.formatted(12, ""));
        ModelDocument theirs = model(APP + CRM, reverse, connected);

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        DocumentNode view = node(result.document(), "id-view");
        assertEquals(1, view.children().size());
        DocumentNode app = view.children().getFirst();
        assertEquals(Optional.empty(), app.attribute("targetConnections"));
        assertTrue(result.cascade().stream().anyMatch(c -> c.kind() == CascadeKind.VIEW_NODE
                && c.archiId().equals(ArchiId.of("id-node-crm"))
                && ArchiId.of("id-view").equals(c.view())));
        assertTrue(result.cascade().stream().anyMatch(c -> c.archiId().equals(ArchiId.of("id-rel2"))));
    }

    @Test
    @DisplayName("INV-IXC-010: геометрия представления в обеих ветвях — представление целиком с одной стороны")
    void viewGeometryIsTakenWholeFromOneSide() {
        ModelDocument base = model(APP + CRM, "", VIEW.formatted(12, CRM_NODE));
        ModelDocument ours = model(APP + CRM, "", VIEW.formatted(100, CRM_NODE));
        ModelDocument theirs = model(APP + CRM, "", VIEW.formatted(12, CRM_NODE.replace("300", "500")));

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        Conflict conflict = single(result.conflicts());
        assertEquals("id-view:" + ModelChanges.DIAGRAM, conflict.id());
        assertEquals("2", conflict.ours());

        Result chosen = EntityMerge.merge(base, ours, theirs, Map.of(conflict.id(), Side.THEIRS));
        assertEquals(Optional.of("12"), node(chosen.document(), "id-node-app").values("bounds").getFirst()
                .attribute("x"), "попиксельно не сливается: координата «нашего» узла не перенесена");
        assertEquals(Optional.of("500"), node(chosen.document(), "id-node-crm").values("bounds").getFirst()
                .attribute("x"));
    }

    @Test
    @DisplayName("INV-IXC-010: обе ветви вставляли в одну папку — оба набора сохраняются у своих соседей")
    void insertsIntoSameFolderKeepBothSets() {
        String x = "<element xsi:type=\"archimate:Node\" name=\"X\" id=\"id-x\"/>";
        String y = "<element xsi:type=\"archimate:Node\" name=\"Y\" id=\"id-y\"/>";
        ModelDocument base = model(APP + CRM, "", "");
        ModelDocument ours = model(APP + CRM + x, "", "");
        ModelDocument theirs = model(APP + y + CRM, "", "");

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        assertEquals(List.of("id-app", "id-y", "id-crm", "id-x"), result.document().folders().getFirst().children()
                .stream().map(n -> n.archiId().value()).toList());
    }

    @Test
    @DisplayName("INV-IXC-010: перестановка в одной ветви и вставка в другой — порядок переставившей ветви")
    void reorderOfOneSideIsKept() {
        String x = "<element xsi:type=\"archimate:Node\" name=\"X\" id=\"id-x\"/>";
        ModelDocument base = model(APP + CRM, "", "");
        ModelDocument ours = model(APP + CRM + x, "", "");
        ModelDocument theirs = model(CRM + APP, "", "");

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        assertEquals(List.of("id-crm", "id-app", "id-x"), result.document().folders().getFirst().children()
                .stream().map(n -> n.archiId().value()).toList());
    }

    @Test
    @DisplayName("INV-IXC-010: удаление папки, в которую другая ветвь положила элемент, — конфликт папки")
    void removedFolderWithForeignWorkIsConflict() {
        String scratch = "<folder name=\"Черновик\" id=\"id-tmp\" type=\"other\">%s</folder>";
        ModelDocument base = document(folder(APP) + scratch.formatted(""));
        ModelDocument ours = document(folder(APP));
        ModelDocument theirs = document(folder(APP) + scratch.formatted(CRM));

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        assertEquals("id-tmp:" + EntityMerge.REMOVAL, single(result.conflicts()).id());
        assertTrue(result.document().find(ArchiId.of("id-crm")).isEmpty());
        assertTrue(result.cascade().stream().anyMatch(c -> c.archiId().equals(ArchiId.of("id-crm"))
                && c.reason() == Reason.FOLDER_REMOVED));

        Result kept = EntityMerge.merge(base, ours, theirs, Map.of("id-tmp:*", Side.THEIRS));
        assertTrue(kept.document().find(ArchiId.of("id-crm")).isPresent());
    }

    @Test
    @DisplayName("INV-IXC-010: ветви переложили папки друг в друга — перенос «их» откатывается")
    void folderCycleRevertsTheirMove() {
        String a = "<folder name=\"A\" id=\"id-a\" type=\"other\">%s</folder>";
        String b = "<folder name=\"B\" id=\"id-b\" type=\"other\">%s</folder>";
        ModelDocument base = document(a.formatted("") + b.formatted(""));
        ModelDocument ours = document(a.formatted(b.formatted("")));
        ModelDocument theirs = document(b.formatted(a.formatted("")));

        Result result = EntityMerge.merge(base, ours, theirs, Map.of());

        assertEquals(List.of("id-a"), result.document().folders().stream().map(f -> f.archiId().value()).toList());
        assertEquals(ArchiId.of("id-b"), result.document().folders().getFirst().children().getFirst().archiId());
        assertEquals(Reason.FOLDER_CYCLE, result.cascade().getFirst().reason());
    }

    @Test
    @DisplayName("INV-IXC-010: выбор по неизвестному конфликту виден как устаревший")
    void unknownResolutionIsReported() {
        ModelDocument base = model(APP, "", "");

        Result result = EntityMerge.merge(base, base, base, Map.of("id-app:name", Side.THEIRS));

        assertEquals(List.of("id-app:name"), result.unknown(Map.of("id-app:name", Side.THEIRS).keySet()));
    }

    @Test
    @DisplayName("FR-02: слияние без правок даёт те же байты эталонной модели")
    void mergeOfUnchangedReferenceIsIdentity() throws IOException {
        byte[] bytes = Files.readAllBytes(REFERENCE);
        ModelDocument reference = read(bytes);

        Result result = EntityMerge.merge(reference, reference, reference, Map.of());

        assertEquals(List.of(), result.conflicts());
        assertEquals(List.of(), result.cascade());
        assertArrayEquals(new ArchiXmlWriter().write(reference), new ArchiXmlWriter().write(result.document()));
    }

    @Test
    @DisplayName("INV-IXC-010: правка одной стороны эталона переносится, остальное байт в байт")
    void oneSidedEditOfReferenceIsApplied() throws IOException {
        ModelDocument reference = read(Files.readAllBytes(REFERENCE));
        String xml = new String(new ArchiXmlWriter().write(reference), StandardCharsets.UTF_8);
        String renamed = xml.replace("name=\"IABS\"", "name=\"IABS 2\"");
        ModelDocument theirs = read(renamed.getBytes(StandardCharsets.UTF_8));

        Result result = EntityMerge.merge(reference, reference, theirs, Map.of());

        assertEquals(renamed, new String(new ArchiXmlWriter().write(result.document()), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("атрибут type развилки и тип ArchiMate — разные поля с разными адресами конфликта")
    void junctionKindIsNotArchimateType() {
        String junction = "<element xsi:type=\"archimate:Junction\" name=\"J\" id=\"id-j\" type=\"or\"/>";
        ModelDocument base = model(junction, "", "");
        ModelDocument ours = model(junction.replace("archimate:Junction", "archimate:OursJunction")
                .replace("type=\"or\"", "type=\"and\""), "", "");
        ModelDocument theirs = model(junction.replace("archimate:Junction", "archimate:TheirsJunction")
                .replace("type=\"or\"", "type=\"xor\""), "", "");

        Result result = EntityMerge.merge(base, ours, theirs, Map.of("id-j:@type", Side.THEIRS));

        assertEquals(List.of("id-j:@type", "id-j:type"),
                result.conflicts().stream().map(Conflict::id).sorted().toList());
        assertEquals(Optional.of("xor"), node(result.document(), "id-j").attribute("type"));
        assertEquals(Optional.of("archimate:OursJunction"), node(result.document(), "id-j").attribute("xsi:type"));
    }

    private static Conflict single(List<Conflict> conflicts) {
        assertEquals(1, conflicts.size(), conflicts::toString);
        return conflicts.getFirst();
    }

    private static DocumentNode node(ModelDocument document, String id) {
        return document.find(ArchiId.of(id)).orElseThrow(() -> new AssertionError(id));
    }

    private static String folder(String content) {
        return "<folder name=\"Приложения\" id=\"id-apps\" type=\"application\">" + content + "</folder>";
    }

    private static ModelDocument model(String elements, String relations, String views) {
        return document("""
                <folder name="Приложения" id="id-apps" type="application">%s</folder>
                <folder name="Связи" id="id-rels" type="relations">%s</folder>
                <folder name="Представления" id="id-views" type="diagrams">%s</folder>"""
                .formatted(elements, relations, views));
    }

    private static ModelDocument document(String content) {
        return read("""
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Тест" id="id-model" version="5.0.0">
                %s
                </archimate:model>
                """.formatted(content).getBytes(StandardCharsets.UTF_8));
    }

    private static ModelDocument read(byte[] xml) {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(xml));
    }
}
