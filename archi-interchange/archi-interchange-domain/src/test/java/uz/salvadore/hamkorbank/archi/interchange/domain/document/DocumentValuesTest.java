package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

class DocumentValuesTest {

    @ParameterizedTest
    @ValueSource(strings = {"name", "xsi:type", "xmlns:archimate", "_x.y-z", "имя"})
    void attributeNameIsXmlName(String name) {
        assertDoesNotThrow(() -> new Attribute(name, "v"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "na me", "1abc", "-x", "a:b:c", ":a", "a:", "a<b", "a=b"})
    void attributeNameOutsideXmlIsRejected(String name) {
        assertThrows(InvalidValueException.class, () -> new Attribute(name, "v"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<feature/>", "<feature name=\"x\">text</feature>", "<archimate:x/>"})
    void fragmentIsElement(String xml) {
        assertDoesNotThrow(() -> new RawXmlFragment(xml, null, DocumentOrder.of(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "<!-- comment -->", "<?pi data?>", "<![CDATA[x]]>", "< feature/>", "<feature",
        "<a/>tail"})
    void fragmentOtherThanElementIsRejected(String xml) {
        assertThrows(InvalidValueException.class, () -> new RawXmlFragment(xml, null, DocumentOrder.of(0)));
    }
}
