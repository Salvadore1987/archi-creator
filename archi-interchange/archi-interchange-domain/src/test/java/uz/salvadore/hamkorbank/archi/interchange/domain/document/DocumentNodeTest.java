package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentNodeTest {

    private static final ArchiId ID = ArchiId.of("id-0123456789abcdef01234567");

    @Test
    @DisplayName("INV-IXC-001: узел без id не строится")
    void nodeWithoutIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentNode("element", DocumentOrder.of(0), Attributes.of("name", "x"), List.of()));
    }

    @Test
    @DisplayName("позиции содержимого обязаны совпадать с местом в списке")
    void contentOrderMustBeDense() {
        DocumentValue misplaced = new DocumentValue("documentation", DocumentOrder.of(1), Attributes.of(), Optional.of("x"));

        assertThrows(IllegalArgumentException.class,
                () -> new DocumentNode("element", DocumentOrder.of(0), Attributes.of("id", ID.value()), List.of(misplaced)));
    }

    @Test
    @DisplayName("фрагмент обязан быть адресован тому узлу, в котором лежит")
    void rawFragmentAddressMustMatchParent() {
        RawXmlFragment elsewhere = new RawXmlFragment("<feature/>",
                Optional.of(ArchiId.of("id-other")), DocumentOrder.of(0));

        assertThrows(IllegalArgumentException.class,
                () -> new DocumentNode("child", DocumentOrder.of(0), Attributes.of("id", ID.value()), List.of(elsewhere)));
    }

    @Test
    @DisplayName("содержимое разбирается по видам без потери порядка")
    void contentIsSlicedByKind() {
        DocumentValue doc = new DocumentValue("documentation", DocumentOrder.of(0), Attributes.of(), Optional.of("описание"));
        RawXmlFragment raw = new RawXmlFragment("<feature name=\"a\"/>", Optional.of(ID), DocumentOrder.of(1));
        DocumentNode child = new DocumentNode("child", DocumentOrder.of(2),
                Attributes.of("xsi:type", "archimate:Note", "id", "id-child"), List.of());
        DocumentNode node = new DocumentNode("element", DocumentOrder.of(0),
                Attributes.of("xsi:type", "archimate:ArchimateDiagramModel", "id", ID.value()), List.of(doc, raw, child));

        assertEquals(Optional.of("описание"), node.documentation());
        assertEquals(List.of(raw), node.rawFragments());
        assertEquals(List.of(child), node.children());
        assertEquals(Optional.of("archimate:ArchimateDiagramModel"), node.archiType());
    }

    @Test
    @DisplayName("атрибут не может повториться")
    void duplicateAttributeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Attributes.of("id", "a", "id", "b"));
    }
}
