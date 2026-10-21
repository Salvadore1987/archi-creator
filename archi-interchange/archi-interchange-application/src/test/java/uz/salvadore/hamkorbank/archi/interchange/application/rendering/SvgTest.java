package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SvgTest {

    @Test
    @DisplayName("число — до сотой, без хвостовых нулей и без минуса у нуля")
    void numbers() {
        assertEquals("12", Svg.num(12));
        assertEquals("0.5", Svg.num(0.5));
        assertEquals("-3.25", Svg.num(-3.249));
        assertEquals("0", Svg.num(-0.001));
    }

    @Test
    @DisplayName("экранирование: разметка — сущностями, управляющие символы срезаются, табуляция остаётся")
    void escaping() {
        assertEquals("a&amp;&lt;&gt;&quot;b", Svg.escape("a&<>\"b"));
        assertEquals("ab\tc", Svg.escape("a\u0001b\tc"));
    }

    @Test
    @DisplayName("краска SVG 1.1: rgba — цвет и отдельная непрозрачность, прочее — как есть, экранированным")
    void paints() {
        assertEquals("fill=\"#FFFFFF\" fill-opacity=\"0.35\"", Svg.paint("fill", "rgba(255, 255, 255, .35)"));
        assertEquals("stroke=\"#010203\"", Svg.paint("stroke", "rgb(1,2,3)"));
        assertEquals("fill=\"#000000\"", Svg.paint("fill", "rgba(0,0,0,1)"));
        assertEquals("fill=\"#FF0000\"", Svg.paint("fill", "rgb(300,0,0)"));
        assertEquals("fill=\"#ff8800\"", Svg.paint("fill", "#ff8800"));
        assertEquals("fill=\"a&quot;b\"", Svg.paint("fill", "a\"b"));
    }
}
