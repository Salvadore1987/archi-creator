package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Box;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.CornerIcon;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.LayerPaint;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.ShapeEntry;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Size;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/** Нотация, шрифт и модель для тестов писателя — малые и предсказуемые. */
final class RenderingFixtures {

    private RenderingFixtures() {
    }

    /** Каждый символ шириной в половину кегля, разрядка — к каждому символу. */
    static final Typesetter MONO = new Typesetter() {
        @Override
        public boolean hasFamily(String family) {
            return family.equals("Inter");
        }

        @Override
        public Metrics metrics(Face face) {
            return new Metrics(face.size() * 0.8, face.size() * 0.2);
        }

        @Override
        public double advance(Face face, String text) {
            int chars = text.codePointCount(0, text.length());
            return chars * face.size() * (0.5 + face.tracking());
        }

        @Override
        public String outline(Face face, String text, double x, double baseline) {
            return "M" + Svg.num(x) + " " + Svg.num(baseline) + " h" + Svg.num(advance(face, text));
        }

        @Override
        public byte[] subset(Face face, String characters) {
            return characters.codePoints().distinct().sorted()
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString().getBytes(StandardCharsets.UTF_8);
        }
    };

    static final TextCatalog TEXTS = message -> message.key() + message.args();

    private static final Box NAME = new Box(8, 5, -22, -10);

    static final Notation NOTATION = new Notation(new Size(120, 55),
            Map.of("archimate:Group", new Size(400, 140), "archimate:Note", new Size(185, 80)),
            Map.of("archimate:ApplicationComponent", new ShapeEntry("rect",
                            Optional.of(new CornerIcon("i-component", -21, 6, 14)), Optional.of(NAME), "node_radius",
                            Optional.of("application"), Optional.empty()),
                    "archimate:Node", new ShapeEntry("rect", Optional.empty(), Optional.of(NAME), "node_radius",
                            Optional.of("technology"), Optional.empty()),
                    "archimate:Group", new ShapeEntry("tab", Optional.empty(), Optional.of(new Box(10, 3, -20, 18)),
                            "node_radius", Optional.of("group"), Optional.empty()),
                    "archimate:Note", new ShapeEntry("dog-ear", Optional.empty(), Optional.of(new Box(9, 7, -18, -14)),
                            "node_radius", Optional.of("note"), Optional.empty())),
            new ShapeEntry("stub", Optional.empty(), Optional.of(NAME), "node_radius", Optional.empty(),
                    Optional.of("5 3")),
            Map.of("application", new LayerPaint("#E3F2FD", "#4A90C2"),
                    "technology", new LayerPaint("#E6F7EC", "#3E9B62"),
                    "motivation", new LayerPaint("#EDE7F6", "#7E57C2"),
                    "other", new LayerPaint("#F2F2F2", "#9E9E9E")),
            Map.ofEntries(Map.entry("node_radius", "8px"), Map.entry("edge_color", "#667085"),
                    Map.entry("edge_width", "1.5px"), Map.entry("edge_corner_radius", "6px"),
                    Map.entry("edge_label_color", "#667085"), Map.entry("paper", "#FAF9F5"),
                    Map.entry("paper_text", "#1A1D22"), Map.entry("grid_step", "20px"),
                    Map.entry("group_fill", "rgba(255,255,255,.35)"), Map.entry("group_border", "#9E9E9E"),
                    Map.entry("note_fill", "#FFFDF2"), Map.entry("note_border", "#E0D9BE"),
                    Map.entry("opaque_border", "#9E9E9E"), Map.entry("node_subtitle_color", "#667085")),
            Map.of("i-component", "<rect x=\"1\" y=\"1\" width=\"14\" height=\"14\" "
                    + "fill=\"var(--icon-cutout, white)\" stroke=\"currentColor\"/>"),
            "Inter");

    /**
     * Компонент и узел в группе, цель фазы 2, заметка; композиция с точкой перегиба,
     * обслуживание из группы наружу, связь, конец которой не на представлении.
     */
    static final String MODEL = """
            <?xml version="1.0" encoding="UTF-8"?>
            <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
            xmlns:archimate="http://www.archimatetool.com/archimate" name="Тест" id="id-m" version="5.0.0">
              <folder name="Application" id="id-fa" type="application">
                <element xsi:type="archimate:ApplicationComponent" name="АБС" id="id-abs"/>
                <element xsi:type="archimate:ApplicationComponent" name="Шлюз" id="id-gw"/>
              </folder>
              <folder name="Technology" id="id-ft" type="technology">
                <element xsi:type="archimate:Node" name="Сервер" id="id-srv"/>
              </folder>
              <folder name="Motivation" id="id-fm" type="motivation">
                <element xsi:type="archimate:Goal" name="Цель" id="id-goal"/>
              </folder>
              <folder name="Relations" id="id-fr" type="relations">
                <element xsi:type="archimate:CompositionRelationship" name="состоит" id="id-r1" source="id-abs" target="id-gw"/>
                <element xsi:type="archimate:ServingRelationship" id="id-r2" source="id-srv" target="id-abs"/>
              </folder>
              <folder name="Views" id="id-fv" type="diagrams">
                <element xsi:type="archimate:ArchimateDiagramModel" name="Схема" id="id-v1">
                  <child xsi:type="archimate:DiagramObject" id="id-n1" targetConnections="id-c2" archimateElement="id-abs" fillColor="#ff8800">
                    <bounds x="40" y="40" width="120" height="55"/>
                    <sourceConnection xsi:type="archimate:Connection" id="id-c1" source="id-n1" target="id-n2" archimateRelationship="id-r1">
                      <bendpoint startX="0" startY="100" endX="-200" endY="0"/>
                    </sourceConnection>
                  </child>
                  <child xsi:type="archimate:DiagramObject" id="id-n2" targetConnections="id-c1" archimateElement="id-gw" font="1|Segoe UI|9.0|1|WINDOWS|1|0|0|0|0|0|0|0|0|1|0|0|0|0|Segoe UI">
                    <bounds x="300" y="200"/>
                  </child>
                  <child xsi:type="archimate:Group" id="id-g" name="Контур">
                    <bounds x="300" y="20" width="200" height="120"/>
                    <child xsi:type="archimate:DiagramObject" id="id-n3" archimateElement="id-srv">
                      <bounds x="20" y="40" width="120" height="55"/>
                      <sourceConnection xsi:type="archimate:Connection" id="id-c2" source="id-n3" target="id-n1" archimateRelationship="id-r2"/>
                    </child>
                  </child>
                  <child xsi:type="archimate:DiagramObject" id="id-n4" archimateElement="id-goal">
                    <bounds x="40" y="300" width="120" height="55"/>
                  </child>
                  <child xsi:type="archimate:Note" id="id-note">
                    <bounds x="600" y="40" width="185" height="80"/>
                    <content>Первая строка
            вторая</content>
                  </child>
                </element>
              </folder>
            </archimate:model>
            """;

    static ModelDocument document(String xml) {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
