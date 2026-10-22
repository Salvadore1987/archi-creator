package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RawXmlTest {

    @Test
    @DisplayName("Пустой остаток XML — не остаток: его отсутствие выражается null")
    void emptyResidueIsRejected() {
        InvalidValueException rejected = assertThrows(InvalidValueException.class, () -> new RawXml(""));

        assertEquals(ModelingMessages.RESIDUE_EMPTY, rejected.reason().key());
    }
}
