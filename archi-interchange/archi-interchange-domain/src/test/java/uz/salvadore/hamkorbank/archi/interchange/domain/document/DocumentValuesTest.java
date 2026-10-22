package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

class DocumentValuesTest {

    @ParameterizedTest
    @ValueSource(strings = {"name", "xsi:type", "xmlns:archimate", "_x.y-z", "имя"})
    @DisplayName("имя атрибута — любое имя XML, с префиксом пространства имён и не только латиницей")
    void attributeNameIsXmlName(String name) {
        assertDoesNotThrow(() -> new Attribute(name, "v"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "na me", "1abc", "-x", "a:b:c", ":a", "a:", "a<b", "a=b"})
    @DisplayName("имя атрибута вне правил XML — отказ")
    void attributeNameOutsideXmlIsRejected(String name) {
        assertThrows(InvalidValueException.class, () -> new Attribute(name, "v"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<feature/>", "<feature name=\"x\">text</feature>", "<archimate:x/>"})
    @DisplayName("фрагмент остатка — один элемент XML")
    void fragmentIsElement(String xml) {
        assertDoesNotThrow(() -> new RawXmlFragment(xml, null, DocumentOrder.of(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<feature value=\"\u0001\"/>", "<feature>\uD800</feature>", "<feature>\uFFFE</feature>"})
    @DisplayName("управляющий символ или одиночный суррогат во фрагменте — отказ при построении, а не нечитаемый файл")
    void fragmentOutsideXmlCharactersIsRejected(String xml) {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> new RawXmlFragment(xml, null, DocumentOrder.of(0)));

        assertEquals(InterchangeMessages.TEXT_NOT_XML, e.reason().key());
        assertEquals(List.of("raw_xml"), e.reason().args());
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "<!-- comment -->", "<?pi data?>", "<![CDATA[x]]>", "< feature/>", "<feature",
        "<a/>tail"})
    @DisplayName("текст, комментарий, инструкция, CDATA или элемент с хвостом вместо фрагмента — отказ")
    void fragmentOtherThanElementIsRejected(String xml) {
        assertThrows(InvalidValueException.class, () -> new RawXmlFragment(xml, null, DocumentOrder.of(0)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    @DisplayName("позиция среди соседей не бывает отрицательной")
    void negativeOrderIsRejected(int value) {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> DocumentOrder.of(value));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.ORDER_NEGATIVE, e.reason().key());
        assertEquals(List.of(value), e.reason().args());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bounds2", "feature", "child", "Documentation"})
    @DisplayName("значение документа — только из тегов, которые читатель понимает как значения")
    void valueWithForeignTagIsRejected(String tag) {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> new DocumentValue(tag, DocumentOrder.of(0), Attributes.of(), "x"));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.NOT_DOCUMENT_VALUE, e.reason().key());
        assertEquals(List.of(tag), e.reason().args());
    }
}
