package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.ShapeEntry;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Size;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

class OefWriterTest {

    private static final Path FIXTURES = Path.of("../../archi-bootstrap/src/test/resources/fixtures");
    private static final Path REFERENCE = Path.of("../../docs/Hamkorbank_AS_IS_strict.archimate");
    private static final OefSchema SCHEMA = OefSchema.load();

    /** Размеры по умолчанию — как в {@code shapes.json}; силуэты писателю не нужны. */
    private static final Notation NOTATION = new Notation(new Size(120, 55),
            Map.of("archimate:Group", new Size(400, 140), "archimate:Note", new Size(185, 80)), Map.of(),
            new ShapeEntry("stub", null, null, "node_radius", null, null), Map.of(), Map.of(), Map.of(), "Inter");

    private static final OefWriter WRITER = new OefWriter(NOTATION);

    @Test
    @DisplayName("FR-43: эталонная модель проходит официальную XSD, все объекты на месте")
    void referenceModelIsSchemaValid() throws IOException {
        ModelDocument document = read(Files.readAllBytes(REFERENCE));
        OefWriter.Written written = WRITER.write(document);
        String xml = text(written);

        assertEquals(Optional.empty(), SCHEMA.violation(written.xml()));
        assertEquals(document.elements().size(), count(xml, "<element "));
        assertEquals(document.relationships().size(), count(xml, "<relationship "));
        assertEquals(document.views().size(), count(xml, "<view "));
        assertTrue(xml.contains("xsi:type=\"Location\""), "Location в редакторе opaque, но в OEF выражается");
        assertEquals(Set.of("type"), written.losses().stream().map(OefLoss::aspect).collect(Collectors.toSet()),
                "в эталоне теряется только тип корневых папок");
        assertEquals(document.folders().size(), written.losses().size());
    }

    @Test
    @DisplayName("INV-IXC-006: остаток XML, неизвестные типы и атрибуты — в отчёте, файл валиден")
    void rawFragmentsAreListedAsLosses() throws IOException {
        OefWriter.Written written = WRITER.write(read(fixture("unknown_extension.archimate")));
        String xml = text(written);
        Set<String> losses = written.losses().stream()
                .map(l -> l.archiId() + " " + l.objectKind().value() + " " + l.aspect())
                .collect(Collectors.toSet());

        assertEquals(Optional.empty(), SCHEMA.violation(written.xml()));
        assertTrue(losses.containsAll(Set.of(
                "id-53f986115270259ff902bf66cf3dca2c element xml:vendor:lineage",
                "id-53f986115270259ff902bf66cf3dca2c element attribute:vendor:owner",
                "id-7af3a3d4e636b362cf09f15c2d17d627 element xml:feature",
                "id-7af3a3d4e636b362cf09f15c2d17d627 element attribute:profiles",
                "id-629bcc564eaeb1d162040c57e7b2d6c4 folder attribute:vendor:color",
                "id-19df3dc10e248cb7b1f4cb91dc5a34af element type",
                "id-11d31d1c2772726fdf87845c581a5b0a relationship type",
                "id-0de635f21d7ffde538d3083b8070ed3d node xml:feature",
                "id-9339b6f33ca58e85115d4f69198f0e72 node viewRef",
                "id-97ca8c3a679cb3760a5e5932f5918a01 node type",
                "id-3a983a300ab35ca41745f730633ff872 view type",
                "id-bc8de74d118ecb853d6c891d4ad0f445 view type",
                "id-054b740bdf40fe499e06add25ef7a09f model xml:profile",
                "id-054b740bdf40fe499e06add25ef7a09f model xml:vendor:metadata")), losses.toString());
        assertFalse(xml.contains("QuantumComponent"));
        assertFalse(xml.contains("identifier=\"id-3a983a300ab35ca41745f730633ff872\""), "набросок не выгружается");
        assertTrue(xml.contains("<label xml:lang=\"ru\">Набросок</label>"), "ссылка на него — подпись без viewRef");
        assertTrue(xml.contains("<item identifierRef=\"id-7af3a3d4e636b362cf09f15c2d17d627\"/>"));
        assertTrue(xml.contains("<name xml:lang=\"ru\">model.owner</name>"), "ключ свойства модели — в словаре");
        assertTrue(written.losses().stream().anyMatch(l -> l.reason().key().equals(InterchangeMessages.OEF_LOSS_RAW)
                && l.reason().args().equals(List.of("vendor:lineage"))));
    }

    @Test
    @DisplayName("FR-44: стиль переносится приблизительно, оформление без аналога — в отчёте")
    void styleIsCarriedApproximately() throws IOException {
        OefWriter.Written written = WRITER.write(read(fixture("styled_objects.archimate")));
        String xml = text(written);
        Set<String> aspects = written.losses().stream().map(l -> l.archiId() + " " + l.aspect())
                .collect(Collectors.toSet());

        assertEquals(Optional.empty(), SCHEMA.violation(written.xml()));
        assertTrue(xml.contains("<fillColor r=\"245\" g=\"246\" b=\"247\" a=\"78\"/>"), xml);
        assertTrue(xml.contains("<font name=\"Segoe UI\" size=\"10\" style=\"bold\">"), xml);
        assertTrue(xml.contains("<style lineWidth=\"3\">"), xml);
        assertTrue(xml.contains("<label xml:lang=\"ru\">Легенда:\nкрасное — платёжный поток;"), xml);
        assertTrue(aspects.containsAll(Set.of(
                "id-231319074ce48620122a10edd5bb895b attribute:connectionRouterType",
                "id-3e74f4054399b88d8d04498898b61213 attribute:textAlignment",
                "id-3e74f4054399b88d8d04498898b61213 attribute:borderType",
                "id-d98400d12fe42b03447ab0ed6de50147 attribute:gradient",
                "id-d98400d12fe42b03447ab0ed6de50147 attribute:iconColor",
                "id-9d76e00d603bb5532673206cc59710ce attribute:nameVisible",
                "id-4b977b44df1f4c2f6961a95641b344b8 attribute:lineAlpha")), aspects.toString());
    }

    @Test
    @DisplayName("§3.5: координаты абсолютные, точки перегиба — от центров концов, как на канве")
    void geometryIsAbsolute() throws IOException {
        String xml = text(WRITER.write(read(fixture("nested_containment.archimate"))));

        assertTrue(xml.contains("<node identifier=\"id-9e2c761aa58843ccc9a605c097a34224\" xsi:type=\"Element\""
                + " elementRef=\"id-cccef779d73ee589dd85b1636c68637b\" x=\"40\" y=\"80\" w=\"150\" h=\"60\""), xml);
        assertTrue(xml.contains("<bendpoint x=\"315\" y=\"110\"/>"), xml);
        assertTrue(xml.contains("<bendpoint x=\"395\" y=\"170\"/>"), xml);
        int group = xml.indexOf("id-945827918f44b60b3d3271ba9f831894");
        int nested = xml.indexOf("id-9e2c761aa58843ccc9a605c097a34224\" xsi:type");
        assertTrue(group > 0 && nested > group, "вложенность — вложенные node");
    }

    @Test
    @DisplayName("§3.5: отрицательные координаты — сдвиг представления; идентификатор не NCName — замена")
    void negativeCoordinatesAndForeignIdentifiers() {
        String archimate = """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:archimate="http://www.archimatetool.com/archimate" name="М" id="id-m" version="5.0.0">
                  <folder name="Application" id="id-f1" type="application">
                    <element xsi:type="archimate:ApplicationComponent" name="А" id="3f2a9c"/>
                    <element xsi:type="archimate:Junction" id="id-j" type="or"/>
                  </folder>
                  <folder name="Relations" id="id-f2" type="relations">
                    <element xsi:type="archimate:AccessRelationship" id="id-r" source="3f2a9c" target="id-j"/>
                  </folder>
                  <folder name="Views" id="id-f3" type="diagrams">
                    <element xsi:type="archimate:ArchimateDiagramModel" name="В" id="id-v" viewpoint="application_cooperation">
                      <child xsi:type="archimate:DiagramObject" id="id-n" archimateElement="3f2a9c">
                        <bounds x="-30" y="-12" width="-1" height="-1"/>
                      </child>
                    </element>
                  </folder>
                </archimate:model>
                """;
        OefWriter.Written written = WRITER.write(read(archimate.getBytes(StandardCharsets.UTF_8)));
        String xml = text(written);

        assertEquals(Optional.empty(), SCHEMA.violation(written.xml()));
        assertTrue(xml.contains("x=\"0\" y=\"0\" w=\"120\" h=\"55\""), xml);
        assertTrue(xml.contains("xsi:type=\"OrJunction\""), xml);
        assertTrue(xml.contains("accessType=\"Write\""), "отсутствие accessType в Archi — запись");
        assertTrue(xml.contains("viewpoint=\"Application Cooperation\""), xml);
        assertFalse(xml.contains("\"3f2a9c\""), xml);
        assertTrue(written.losses().stream().anyMatch(l -> l.aspect().equals("coordinates")
                && l.reason().args().equals(List.of("30", "12"))), written.losses().toString());
        assertTrue(written.losses().stream().anyMatch(l -> l.archiId().equals("3f2a9c")
                && l.aspect().equals("identifier")), written.losses().toString());
    }

    @Test
    @DisplayName("§3.5: связь к связи — к середине маршрута ребра-конца, как на канве; цепочка не роняет выгрузку")
    void connectionToConnectionChain() {
        String archimate = """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:archimate="http://www.archimatetool.com/archimate" name="М" id="id-m" version="5.0.0">
                  <folder name="Application" id="id-f1" type="application">
                    <element xsi:type="archimate:ApplicationComponent" name="A" id="id-a"/>
                    <element xsi:type="archimate:ApplicationComponent" name="B" id="id-b"/>
                    <element xsi:type="archimate:ApplicationComponent" name="C" id="id-c"/>
                    <element xsi:type="archimate:ApplicationComponent" name="D" id="id-d"/>
                  </folder>
                  <folder name="Relations" id="id-f2" type="relations">
                    <element xsi:type="archimate:FlowRelationship" id="id-r1" source="id-a" target="id-b"/>
                    <element xsi:type="archimate:AssociationRelationship" id="id-r2" source="id-c" target="id-r1"/>
                    <element xsi:type="archimate:AssociationRelationship" id="id-r3" source="id-d" target="id-r2"/>
                  </folder>
                  <folder name="Views" id="id-f3" type="diagrams">
                    <element xsi:type="archimate:ArchimateDiagramModel" name="В" id="id-v">
                      <child xsi:type="archimate:DiagramObject" id="id-na" archimateElement="id-a">
                        <bounds x="0" y="0" width="100" height="40"/>
                        <sourceConnection xsi:type="archimate:Connection" id="id-e1" targetConnections="id-e2" source="id-na" target="id-nb" archimateRelationship="id-r1"/>
                      </child>
                      <child xsi:type="archimate:DiagramObject" id="id-nb" targetConnections="id-e1" archimateElement="id-b">
                        <bounds x="300" y="0" width="100" height="40"/>
                      </child>
                      <child xsi:type="archimate:DiagramObject" id="id-nc" archimateElement="id-c">
                        <bounds x="150" y="200" width="100" height="40"/>
                        <sourceConnection xsi:type="archimate:Connection" id="id-e2" targetConnections="id-e3" source="id-nc" target="id-e1" archimateRelationship="id-r2"/>
                      </child>
                      <child xsi:type="archimate:DiagramObject" id="id-nd" archimateElement="id-d">
                        <bounds x="400" y="200" width="100" height="40"/>
                        <sourceConnection xsi:type="archimate:Connection" id="id-e3" source="id-nd" target="id-e2" archimateRelationship="id-r3">
                          <bendpoint startY="-100"/>
                        </sourceConnection>
                      </child>
                    </element>
                  </folder>
                </archimate:model>
                """;
        OefWriter.Written written = WRITER.write(read(archimate.getBytes(StandardCharsets.UTF_8)));
        String xml = text(written);

        assertEquals(Optional.empty(), SCHEMA.violation(written.xml()));
        // Середина маршрута e1 — (200, 20), маршрут e2 вертикален от низа до неё: середина (200, 110.25).
        // Точка перегиба e3 — пополам между (450, 220 - 100) и (200, 110.25).
        assertTrue(xml.contains("<bendpoint x=\"325\" y=\"115\"/>"), xml);
    }

    @Test
    @DisplayName("FR-44: строка шрифта без начертания — как в картинке: шрифт переносится, начертание обычное")
    void fontWithoutStyleIsCarried() {
        String archimate = """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:archimate="http://www.archimatetool.com/archimate" name="М" id="id-m" version="5.0.0">
                  <folder name="Application" id="id-f1" type="application">
                    <element xsi:type="archimate:ApplicationComponent" name="А" id="id-a"/>
                  </folder>
                  <folder name="Views" id="id-f3" type="diagrams">
                    <element xsi:type="archimate:ArchimateDiagramModel" name="В" id="id-v">
                      <child xsi:type="archimate:DiagramObject" id="id-n" archimateElement="id-a" font="1|Arial|10.0">
                        <bounds x="0" y="0" width="120" height="55"/>
                      </child>
                    </element>
                  </folder>
                </archimate:model>
                """;
        OefWriter.Written written = WRITER.write(read(archimate.getBytes(StandardCharsets.UTF_8)));

        assertTrue(text(written).contains("<font name=\"Arial\" size=\"10\" style=\"plain\"/>"), text(written));
        assertFalse(written.losses().stream().anyMatch(l -> l.aspect().equals("style:font")),
                written.losses().toString());
    }

    @Test
    @DisplayName("INV-IXC-004: тот же документ — те же байты")
    void writingTwiceProducesIdenticalBytes() throws IOException {
        ModelDocument document = read(fixture("all_relationship_types.archimate"));
        assertArrayEquals(WRITER.write(document).xml(), WRITER.write(document).xml());
    }

    @Test
    @DisplayName("FR-43: каждая фикстура даёт файл, валидный по XSD")
    void everyFixtureIsSchemaValid() throws IOException {
        try (Stream<Path> files = Files.list(FIXTURES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".archimate")).sorted().toList()) {
                assertEquals(Optional.empty(), SCHEMA.violation(WRITER.write(read(Files.readAllBytes(file))).xml()),
                        file.getFileName().toString());
            }
        }
    }

    private static ModelDocument read(byte[] bytes) {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(bytes));
    }

    private static byte[] fixture(String name) throws IOException {
        return Files.readAllBytes(FIXTURES.resolve(name));
    }

    private static String text(OefWriter.Written written) {
        return new String(written.xml(), StandardCharsets.UTF_8);
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }
}
