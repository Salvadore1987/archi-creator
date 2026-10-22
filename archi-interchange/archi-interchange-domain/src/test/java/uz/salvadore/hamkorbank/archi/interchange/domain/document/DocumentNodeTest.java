package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

class DocumentNodeTest {

    private static final ArchiId ID = ArchiId.of("id-0123456789abcdef01234567");

    @Test
    @DisplayName("INV-IXC-001: узел без id не строится")
    void nodeWithoutIdIsRejected() {
        assertThrows(InvalidValueException.class,
                () -> new DocumentNode("element", DocumentOrder.of(0), Attributes.of("name", "x"), List.of()));
    }

    @Test
    @DisplayName("позиции содержимого обязаны совпадать с местом в списке")
    void contentOrderMustBeDense() {
        DocumentValue misplaced = new DocumentValue("documentation", DocumentOrder.of(1), Attributes.of(),
                "x");

        assertThrows(InvalidValueException.class,
                () -> new DocumentNode("element", DocumentOrder.of(0), Attributes.of("id", ID.value()),
                        List.of(misplaced)));
    }

    @Test
    @DisplayName("фрагмент обязан быть адресован тому узлу, в котором лежит")
    void rawFragmentAddressMustMatchParent() {
        RawXmlFragment elsewhere = new RawXmlFragment("<feature/>",
                ArchiId.of("id-other"), DocumentOrder.of(0));

        assertThrows(InvalidValueException.class,
                () -> new DocumentNode("child", DocumentOrder.of(0), Attributes.of("id", ID.value()),
                        List.of(elsewhere)));
    }

    @Test
    @DisplayName("содержимое разбирается по видам без потери порядка")
    void contentIsSlicedByKind() {
        DocumentValue doc = new DocumentValue("documentation", DocumentOrder.of(0), Attributes.of(),
                "описание");
        RawXmlFragment raw = new RawXmlFragment("<feature name=\"a\"/>", ID, DocumentOrder.of(1));
        DocumentNode child = new DocumentNode("child", DocumentOrder.of(2),
                Attributes.of("xsi:type", "archimate:Note", "id", "id-child"), List.of());
        DocumentNode node = new DocumentNode("element", DocumentOrder.of(0),
                Attributes.of("xsi:type", "archimate:ArchimateDiagramModel", "id", ID.value()),
                List.of(doc, raw, child));

        assertEquals(Optional.of("описание"), node.documentation());
        assertEquals(List.of(raw), node.rawFragments());
        assertEquals(List.of(child), node.children());
        assertEquals(Optional.of("archimate:ArchimateDiagramModel"), node.archiType());
    }

    @Test
    @DisplayName("атрибут не может повториться")
    void duplicateAttributeIsRejected() {
        assertThrows(InvalidValueException.class, () -> Attributes.of("id", "a", "id", "b"));
    }

    @Test
    @DisplayName("пары имя–значение без значения у последнего имени не собираются в атрибуты")
    void oddNamesAndValuesAreRejected() {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> Attributes.of("id", "id-e1", "name"));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.ATTRIBUTE_PAIRS_EXPECTED, e.reason().key());
    }

    @ParameterizedTest
    @ValueSource(strings = {"documentation", "model", "diagram", "Element"})
    @DisplayName("узел документа — только из тегов, которые читатель понимает как узлы")
    void nodeWithForeignTagIsRejected(String tag) {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> new DocumentNode(tag, DocumentOrder.of(0), Attributes.of("id", ID.value()), List.of()));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.NOT_DOCUMENT_NODE, e.reason().key());
        assertEquals(List.of(tag), e.reason().args());
    }

    @Test
    @DisplayName("Значение документа без символов вне XML 1.0: иначе выгрузка нечитаема или искажена")
    void textOutsideXmlIsRejected() {
        assertThrows(InvalidValueException.class, () -> new Attribute("name", "а\u0001б"));
        assertThrows(InvalidValueException.class, () -> new Attribute("name", "\uD800"));
        assertThrows(InvalidValueException.class, () -> new DocumentValue("documentation", DocumentOrder.of(0),
                new Attributes(List.of()), "\uFFFE"));
        new Attribute("name", "таб\tи\nстрока \uD83D\uDE00");
    }
}
