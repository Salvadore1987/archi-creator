package uz.salvadore.hamkorbank.archi.bootstrap.roundtrip;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.XmlEquivalence.assertXmlEquivalent;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;

/**
 * Проверка самой проверки: сравнение, которое прощает лишнее, сделало бы golden-file
 * тесты зелёными над сломанным round-trip.
 */
class XmlEquivalenceTest {

    private static final String BASE = """
            <m:model xmlns:m="urn:m" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" id="id-1" name="М">
              <folder id="id-2" name="A">
                <element xsi:type="m:X" id="id-3" name="x"><documentation>текст</documentation></element>
                <element xsi:type="m:Y" id="id-4" name="y"/>
              </folder>
            </m:model>""";

    @ParameterizedTest
    @ValueSource(strings = {
            // другой отступ и переводы строк между элементами
            """
            <m:model xmlns:m="urn:m" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" id="id-1" name="М"><folder id="id-2" name="A">
            <element xsi:type="m:X" id="id-3" name="x">
                <documentation>текст</documentation>
            </element><element xsi:type="m:Y" id="id-4" name="y"></element></folder></m:model>""",
            // пустой элемент развёрнут, внутри только перевод строки и отступ
            """
            <m:model xmlns:m="urn:m" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" id="id-1" name="М">
              <folder id="id-2" name="A">
                <element xsi:type="m:X" id="id-3" name="x"><documentation>текст</documentation></element>
                <element xsi:type="m:Y" id="id-4" name="y">
                </element>
              </folder>
            </m:model>""",
            // другой порядок атрибутов и другой префикс того же пространства
            """
            <a:model xmlns:a="urn:m" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="М" id="id-1">
              <folder name="A" id="id-2">
                <element name="x" id="id-3" xsi:type="m:X"><documentation>текст</documentation></element>
                <!-- комментарий -->
                <element id="id-4" xsi:type="m:Y" name="y"/>
              </folder>
            </a:model>"""})
    @DisplayName("форматирование не считается расхождением")
    void formattingIsForgiven(String variant) {
        assertDoesNotThrow(() -> assertXmlEquivalent(bytes(BASE), bytes(variant)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "порядок узлов|<element xsi:type=\"m:X\" id=\"id-3\" name=\"x\"><documentation>текст</documentation></element>\n"
                    + "    <element xsi:type=\"m:Y\" id=\"id-4\" name=\"y\"/>|<element xsi:type=\"m:Y\" id=\"id-4\" name=\"y\"/>\n"
                    + "    <element xsi:type=\"m:X\" id=\"id-3\" name=\"x\"><documentation>текст</documentation></element>",
            "id|id=\"id-4\"|id=\"id-5\"",
            "атрибут пропал| name=\"y\"|",
            "атрибут добавлен|name=\"y\"|name=\"y\" fillColor=\"#ffffff\"",
            "текст|>текст<|>текст.<",
            "пробел в тексте|>текст<|> текст<",
            "лишний узел|<element xsi:type=\"m:Y\" id=\"id-4\" name=\"y\"/>|<element xsi:type=\"m:Y\" id=\"id-4\" name=\"y\"/><property key=\"k\"/>",
            "тип|m:Y|m:Z"})
    @DisplayName("смысловое расхождение не прощается")
    void semanticDifferenceFails(String testCase) {
        String[] parts = testCase.split("\\|", -1);
        String changed = BASE.replace(parts[1], parts[2]);
        if (changed.equals(BASE)) {
            throw new IllegalStateException("подмена «" + parts[0] + "» ничего не изменила");
        }
        assertThrows(AssertionFailedError.class, () -> assertXmlEquivalent(bytes(BASE), bytes(changed)), parts[0]);
    }

    private static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
