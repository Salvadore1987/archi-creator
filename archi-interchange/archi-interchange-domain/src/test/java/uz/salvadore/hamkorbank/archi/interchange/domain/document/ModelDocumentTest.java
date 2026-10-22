package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

class ModelDocumentTest {

    private static final ArchiId MODEL = ArchiId.of("id-0123456789abcdef01234567");
    private static final ArchiId OTHER = ArchiId.of("id-other");

    @Test
    @DisplayName("фрагмент в корне модели не может быть адресован другому узлу")
    void rootFragmentAddressedElsewhereIsRejected() {
        RawXmlFragment elsewhere = new RawXmlFragment("<feature/>", OTHER, DocumentOrder.of(0));
        Attributes attributes = Attributes.of("id", MODEL.value());
        List<DocumentContent> content = List.of(elsewhere);

        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> new ModelDocument(attributes, content));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.FRAGMENT_ROOT_MISADDRESSED, e.reason().key());
        assertEquals(List.of(OTHER), e.reason().args());
    }
}
