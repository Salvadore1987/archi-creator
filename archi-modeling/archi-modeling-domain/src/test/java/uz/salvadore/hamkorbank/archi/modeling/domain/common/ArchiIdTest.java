package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArchiIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "-id", ".id", "id 1", "id/1", "ид-1"})
    @DisplayName("Идентификатор Archi — буквы латиницы, цифры, подчёркивание, точка и дефис, не с точки и дефиса")
    void malformedArchiIdIsRejected(String value) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class, () -> new ArchiId(value));

        assertEquals(ModelingMessages.ARCHI_ID_INVALID, rejected.reason().key());
    }
}
