package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;

class ArchiReaderTest {

    private static final String VIEW = """
            <folder name="Views" id="id-f00000000000000000000008" type="diagrams">
              <element xsi:type="archimate:ArchimateDiagramModel" name="01 Контекст" id="id-v00000000000000000000001">
                <documentation>Строка 1
            Строка 2</documentation>
                <child xsi:type="archimate:Group" id="id-g00000000000000000000001" name="Каналы">
                  <bounds x="10" y="20" width="400" height="300"/>
                  <child xsi:type="archimate:DiagramObject" id="id-d00000000000000000000001" targetConnections="id-c00000000000000000000002 id-c00000000000000000000001" archimateElement="id-e00000000000000000000001">
                    <bounds x="20" y="60" width="150" height="60"/>
                  </child>
                </child>
                <child xsi:type="archimate:DiagramObject" id="id-d00000000000000000000002" archimateElement="id-e00000000000000000000002">
                  <bounds x="500" y="60" width="150" height="60"/>
                  <sourceConnection xsi:type="archimate:Connection" id="id-c00000000000000000000001" source="id-d00000000000000000000002" target="id-d00000000000000000000001" archimateRelationship="id-r00000000000000000000001"/>
                  <sourceConnection xsi:type="archimate:Connection" id="id-c00000000000000000000002" source="id-d00000000000000000000002" target="id-d00000000000000000000001"/>
                </child>
              </element>
            </folder>
            """;

    private static final String MODEL = """
            <folder name="Application" id="id-f00000000000000000000003" type="application">
              <folder name="Каналы" id="id-f00000000000000000000031">
                <element xsi:type="archimate:ApplicationComponent" name="HamkorMobile" id="id-e00000000000000000000001">
                  <property key="source" value="vsdx"/>
                </element>
              </folder>
              <element xsi:type="archimate:ApplicationComponent" name="ABS" id="id-e00000000000000000000002"/>
            </folder>
            <folder name="Relations" id="id-f00000000000000000000007" type="relations">
              <element xsi:type="archimate:FlowRelationship" id="id-r00000000000000000000001" source="id-e00000000000000000000002" target="id-e00000000000000000000001"/>
            </folder>
            """ + VIEW + "<purpose>Модель для теста</purpose>";

    @Test
    @DisplayName("INV-IXC-001: неизвестный узел сохраняется непрозрачным фрагментом с адресом и позицией")
    void unknownNodeIsPreservedAsRawFragment() {
        ModelDocument document = Xml.read(Xml.model("""
                <folder name="Other" id="id-f00000000000000000000006" type="other">
                  <element xsi:type="archimate:Note" name="x" id="id-e00000000000000000000009">
                    <property key="a" value="1"/>
                    <feature name="labelExpression" value="${name} &amp; &quot;co&quot;">
                      <extension kind="тест"/>
                    </feature>
                  </element>
                </folder>
                <profile name="Банк" id="id-p00000000000000000000001" conceptType="ApplicationComponent"/>
                """));

        DocumentNode element = document.folders().getFirst().children().getFirst();
        RawXmlFragment feature = assertInstanceOf(RawXmlFragment.class, element.content().get(1));
        assertEquals(Optional.of(ArchiId.of("id-e00000000000000000000009")), feature.parentArchiId());
        assertEquals(1, feature.order().value());
        // Внутренние пробелы — как в файле: фрагмент стоит на глубине 4, его ребёнок — на 6.
        assertEquals("""
                <feature name="labelExpression" value="${name} &amp; &quot;co&quot;">
                      <extension kind="тест"/>
                    </feature>""", feature.xml());

        RawXmlFragment profile = document.orphanFragments().getFirst();
        assertEquals(Optional.empty(), profile.parentArchiId());
        assertEquals(1, profile.order().value());
        assertTrue(profile.xml().startsWith("<profile name=\"Банк\" id=\"id-p00000000000000000000001\""));
    }

    @Test
    @DisplayName("INV-IXC-001: незнакомый атрибут хранится на своём месте среди знакомых")
    void unknownAttributeIsKeptInPlace() {
        ModelDocument document = Xml.read(Xml.model("""
                <folder name="Business" id="id-f00000000000000000000002" type="business" futureFlag="да">
                  <element xsi:type="archimate:BusinessActor" archi9:hint="x" name="Клиент" id="id-e00000000000000000000003" xmlns:archi9="urn:archi9"/>
                </folder>
                """));

        DocumentNode folder = document.folders().getFirst();
        assertEquals(List.of("name", "id", "type", "futureFlag"), names(folder));
        assertEquals(List.of("xmlns:archi9", "xsi:type", "archi9:hint", "name", "id"), names(folder.children().getFirst()));
    }

    @Test
    @DisplayName("ADR-0002: идентификаторы сохраняются буквально, в том числе 32-hex из самого Archi")
    void idsArePreservedLiterally() {
        ModelDocument document = Xml.read(Xml.model("""
                <folder name="Business" id="id-4c1f0f8e2a2b4d3e9f8a7b6c5d4e3f2a" type="business">
                  <element xsi:type="archimate:BusinessActor" name="Клиент" id="4fe7b3cd"/>
                </folder>
                """));

        assertEquals("id-4c1f0f8e2a2b4d3e9f8a7b6c5d4e3f2a", document.folders().getFirst().archiId().value());
        assertEquals("4fe7b3cd", document.elements().getFirst().archiId().value());
        assertEquals(Xml.MODEL_ID, document.archiId().value());
    }

    @Test
    @DisplayName("INV-MDL-009: папки — дерево с вложенностью, элементы лежат в своих папках")
    void foldersAreKeptAsTree() {
        ModelDocument document = Xml.read(Xml.model(MODEL));

        assertEquals(List.of("application", "relations", "diagrams"),
                document.folders().stream().map(f -> f.attribute("type").orElseThrow()).toList());
        DocumentNode nested = document.folders().getFirst().children().getFirst();
        assertEquals("folder", nested.tag());
        assertEquals(Optional.empty(), nested.attribute("type"));
        assertEquals("HamkorMobile", nested.children().getFirst().attribute("name").orElseThrow());
        assertEquals(2, document.elements().size());
        assertEquals(1, document.relationships().size());
        assertEquals(1, document.views().size());
        assertEquals(Optional.of("Модель для теста"), document.documentation());
    }

    @Test
    @DisplayName("§3.4 п. 3: порядок содержимого фиксируется плотно, с нуля, вперемешку по видам")
    void orderIsRecorded() {
        DocumentNode view = Xml.read(Xml.model(MODEL)).views().getFirst();

        List<DocumentContent> content = view.content();
        assertEquals(List.of(0, 1, 2), content.stream().map(c -> c.order().value()).toList());
        assertInstanceOf(DocumentValue.class, content.get(0));
        assertEquals("id-g00000000000000000000001", ((DocumentNode) content.get(1)).archiId().value());
        assertEquals("id-d00000000000000000000002", ((DocumentNode) content.get(2)).archiId().value());
    }

    @Test
    @DisplayName("§3.4 п. 4: bounds у ребёнка группы остаются относительными группе")
    void boundsStayRelativeToParent() {
        DocumentNode group = Xml.read(Xml.model(MODEL)).views().getFirst().children().getFirst();
        DocumentValue nested = group.children().getFirst().values("bounds").getFirst();

        assertEquals(Optional.of("20"), nested.attribute("x"));
        assertEquals(Optional.of("60"), nested.attribute("y"));
    }

    @Test
    @DisplayName("FR-01: свойства, документация и описание модели читаются дословно")
    void propertiesAndDocumentationAreRead() {
        ModelDocument document = Xml.read(Xml.model(MODEL));

        DocumentValue property = document.elements().getFirst().values("property").getFirst();
        assertEquals(Optional.of("source"), property.attribute("key"));
        assertEquals(Optional.of("vsdx"), property.attribute("value"));
        assertEquals(Optional.of("Строка 1\nСтрока 2"), document.views().getFirst().documentation());
    }

    @Test
    @DisplayName("§3.4 п. 6: порядок targetConnections хранится как записан, а не как идут соединения")
    void targetConnectionsOrderIsKept() {
        DocumentNode target = Xml.read(Xml.model(MODEL)).find(ArchiId.of("id-d00000000000000000000001")).orElseThrow();

        assertEquals(Optional.of("id-c00000000000000000000002 id-c00000000000000000000001"),
                target.attribute("targetConnections"));
    }

    @Test
    @DisplayName("файл не Archi — отказ с указанием корня")
    void foreignRootIsRejected() {
        CorruptDocumentException e = assertThrows(CorruptDocumentException.class,
                () -> Xml.read("<?xml version=\"1.0\"?>\n<model xmlns=\"urn:other\" id=\"id-1\"/>"));

        assertEquals(DocumentDefect.NOT_ARCHIMATE_MODEL, e.defects().getFirst().code());
    }

    private static List<String> names(DocumentNode node) {
        return node.attributes().list().stream().map(Attribute::name).toList();
    }
}
