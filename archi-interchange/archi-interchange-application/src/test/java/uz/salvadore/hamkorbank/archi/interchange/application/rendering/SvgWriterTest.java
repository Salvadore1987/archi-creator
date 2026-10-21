package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportLoss;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossObjectKind;

class SvgWriterTest {

    private static final SvgWriter.Options TEXT = new SvgWriter.Options(false, false);
    private static final SvgWriter.Options OUTLINE = new SvgWriter.Options(true, false);

    private final SvgWriter writer = new SvgWriter(RenderingFixtures.NOTATION, RenderingFixtures.MONO,
            RenderingFixtures.TEXTS);

    @Test
    @DisplayName("FR-41: картинка целиком по содержимому с полями в шаг сетки")
    void imageCoversContentWithMargin() {
        SvgWriter.Rendered rendered = render(TEXT);

        assertEquals(20, rendered.x(), "левый край содержимого 40 минус поле");
        assertEquals(0, rendered.y(), "группа начинается с 20");
        assertEquals(805, rendered.x() + rendered.width(), "заметка кончается на 785 плюс поле");
        assertTrue(svg(rendered).contains("viewBox=\"20 0 785 "), svg(rendered));
    }

    @Test
    @DisplayName("FR-21: собственная заливка сильнее токена слоя; иначе — цвет слоя")
    void ownFillBeatsLayerToken() {
        String svg = svg(render(TEXT));
        assertTrue(svg.contains("fill=\"#ff8800\" stroke=\"#4A90C2\""), svg);
        assertTrue(svg.contains("fill=\"#E6F7EC\" stroke=\"#3E9B62\""), "узел технологий — свой слой");
        assertTrue(svg.contains("fill=\"#FFFFFF\" fill-opacity=\"0.35\""), "rgba группы — цвет и непрозрачность");
    }

    @Test
    @DisplayName("UI-019: иконка — копия символа на месте, цвет обводки и вырез подставлены")
    void iconIsInlinedWithColors() {
        String svg = svg(render(TEXT));
        assertTrue(svg.contains("<g transform=\"translate(99 6) scale(0.88)\"><rect x=\"1\" y=\"1\" width=\"14\" "
                + "height=\"14\" fill=\"#ff8800\" stroke=\"#4A90C2\"/></g>"), svg);
        assertFalse(svg.contains("currentColor") || svg.contains("var("), "переменных CSS в файле нет");
    }

    @Test
    @DisplayName("INV-IXC-006: цвет из файла не по правилу формата — токен слоя и запись отчёта, не разметка")
    void foreignColorIsNotMarkup() {
        String xml = RenderingFixtures.MODEL.replace("fillColor=\"#ff8800\"",
                "fillColor=\"x&quot;/&gt;&lt;script&gt;1&lt;/script&gt;\" lineColor=\"red&quot; onload=&quot;1\"");
        SvgWriter.Rendered rendered = render(RenderingFixtures.document(xml), TEXT);
        String svg = svg(rendered);

        assertFalse(svg.contains("<script") || svg.contains("onload"), svg);
        assertTrue(svg.contains("fill=\"#E3F2FD\" stroke=\"#4A90C2\"/></g>"), "иконка — цветом слоя: " + svg);
        assertEquals(List.of("fillColor", "lineColor"), rendered.losses().stream()
                .filter(l -> l.archiId().equals("id-n1") && l.aspect().equals("style"))
                .map(l -> l.reason().args().getFirst().toString()).toList(), rendered.losses().toString());
    }

    @Test
    @DisplayName("FR-41: наконечник — явная геометрия; ромб композиции развёрнут у начала связи")
    void markersAreExplicitPaths() {
        String svg = svg(render(TEXT));
        assertFalse(svg.contains("<marker"));
        assertTrue(svg.contains("d=\"M1 5 L7 1.4 L13 5 L7 8.6 Z\" fill=\"#667085\""), "композиция — сплошной ромб");
        assertTrue(svg.contains("d=\"M2.5 1.5 L9.5 5 L2.5 8.5\" fill=\"none\""), "обслуживание — открытая стрелка");
    }

    @Test
    @DisplayName("порядок как у канвы: рёбра уровня раньше его узлов, вложенный узел — после группы")
    void paintOrderFollowsCanvas() {
        String svg = svg(render(TEXT));
        int composition = svg.indexOf("M1 5 L7 1.4 L13 5");
        int firstNode = svg.indexOf("<g transform=\"translate(40 40)\">");
        int group = svg.indexOf("<g transform=\"translate(300 20)\">");
        int nested = svg.indexOf("<g transform=\"translate(320 60)\">");
        int serving = svg.indexOf("M2.5 1.5 L9.5 5");
        assertTrue(composition < firstNode, "ребро уровня 0 под узлами уровня 0");
        assertTrue(group < serving && serving < nested, "ребро из группы — над группой, под вложенным узлом");
    }

    @Test
    @DisplayName("INV-IXC-006: заглушка, подменённый шрифт и обрыв связи — в отчёте о потерях")
    void lossesAreReported() {
        String xml = RenderingFixtures.MODEL.replace(
                "</element>\n              </folder>\n            </archimate:model>",
                "  <sourceConnection xsi:type=\"archimate:Connection\" id=\"id-c9\" source=\"id-n4\" target=\"id-zz\"/>"
                        + "\n                </element>\n              </folder>\n            </archimate:model>");
        List<ExportLoss> losses = render(RenderingFixtures.document(xml), TEXT).losses();

        assertTrue(losses.contains(new ExportLoss("id-n4", LossObjectKind.NODE, "shape",
                Message.of(
                        "interchange.image.loss-placeholder", "WorkPackage"))), losses.toString());
        assertTrue(losses.stream().anyMatch(l -> l.archiId().equals("id-n2") && l.aspect().equals("font")));
        assertEquals(List.of("id-n2", "id-n4"), losses.stream().filter(l -> !l.aspect().equals("route"))
                .map(ExportLoss::archiId).distinct().sorted().toList());
    }

    @Test
    @DisplayName("заглушка обведена цветом неподдержанного и подписана типом")
    void opaqueNodeIsPlaceholder() {
        String svg = svg(render(TEXT));
        assertTrue(svg.contains("stroke=\"#9E9E9E\" stroke-width=\"1\" stroke-dasharray=\"5 3\""), svg);
        assertTrue(svg.contains(">interchange.ima…<"), "подзаголовок заглушки усечён по ширине");
    }

    @Test
    @DisplayName("FR-41: текстовый SVG вшивает подмножество шрифта; в кривых — ни текста, ни шрифта")
    void textModeEmbedsFontOutlineModeDoesNot() {
        String text = svg(render(TEXT));
        assertTrue(text.contains("@font-face{font-family:\"Inter\";font-weight:500"), text);
        assertTrue(text.contains("<text "));
        assertTrue(text.contains("xml:space=\"preserve\""), "заметка хранит пробелы");

        String outline = svg(render(OUTLINE));
        assertFalse(outline.contains("<text") || outline.contains("@font-face"), outline);
    }

    @Test
    @DisplayName("ярлык группы — капителью, слева, разрядкой")
    void groupTabLabel() {
        String svg = svg(render(TEXT));
        assertTrue(svg.contains("font-size=\"11\" font-weight=\"600\" letter-spacing=\"0.44\" fill=\"#667085\">КОНТУР"),
                svg);
    }

    @Test
    @DisplayName("INV-IXC-004 для картинки: та же версия — те же байты")
    void deterministic() {
        assertArrayEquals(render(TEXT).svg(), render(TEXT).svg());
    }

    @Test
    @DisplayName("фон прозрачный — без подложки цвета бумаги")
    void transparentBackground() {
        String svg = svg(render(new SvgWriter.Options(false, true)));
        assertFalse(svg.contains("<rect x=\"20\" y=\"0\""), svg);
    }

    private SvgWriter.Rendered render(SvgWriter.Options options) {
        return render(RenderingFixtures.document(RenderingFixtures.MODEL), options);
    }

    private SvgWriter.Rendered render(ModelDocument document, SvgWriter.Options options) {
        DocumentNode view = document.views().getFirst();
        return writer.write(document, view, options);
    }

    private static String svg(SvgWriter.Rendered rendered) {
        return new String(rendered.svg(), StandardCharsets.UTF_8);
    }
}
