package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter.Face;

/** Символ — 5 px при кегле 10: строка шириной 50 вмещает десять символов. */
class LabelLayoutTest {

    private static final Face FACE = new Face("Inter", 10, 500, false, 0);
    private final LabelLayout layout = new LabelLayout(RenderingFixtures.MONO);

    @Test
    @DisplayName("FR-42: перенос жадный по словам, хвостовой пробел ширины не занимает")
    void greedyWordWrap() {
        LabelLayout.Lines lines = layout.wrap("Платёж   на сервер", FACE, 50, 2, false);
        assertEquals(List.of("Платёж на", "сервер"), lines.lines());
        assertFalse(lines.truncated());
    }

    @Test
    @DisplayName("FR-42: разрыв после дефиса; слово шире строки рвётся по символам")
    void breakAfterHyphenAndInsideLongWord() {
        assertEquals(List.of("Бизнес-", "процесс"), layout.wrap("Бизнес-процесс", FACE, 50, 0, false).lines());
        assertEquals(List.of("Технологич", "еский"), layout.wrap("Технологический", FACE, 50, 0, false).lines());
    }

    @Test
    @DisplayName("FR-42: сверх двух строк — многоточие в конце второй и признак усечения")
    void clampWithEllipsis() {
        LabelLayout.Lines lines = layout.wrap("один два три четыре пять шесть", FACE, 50, 2, false);
        assertEquals(List.of("один два", "три четыр…"), lines.lines());
        assertTrue(lines.truncated());
    }

    @Test
    @DisplayName("заметка: переводы строк и пробелы значимы, предела строк нет")
    void preservedText() {
        assertEquals(List.of("a  b", "", "c"), layout.wrap("a  b\n\nc", FACE, 50, 0, true).lines());
    }

    @Test
    @DisplayName("одна строка без переноса обрезается многоточием")
    void singleLine() {
        assertEquals(List.of("Тип не по…"), layout.single("Тип не поддержан", FACE, 50).lines());
    }
}
