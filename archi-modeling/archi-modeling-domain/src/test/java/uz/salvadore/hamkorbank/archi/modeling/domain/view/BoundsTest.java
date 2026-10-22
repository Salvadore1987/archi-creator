package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

class BoundsTest {

    @ParameterizedTest
    @CsvSource({"0, 55", "120, 0", "-2, 55", "120, -5"})
    @DisplayName("Размер узла — положительный или «по умолчанию» Archi (-1)")
    void invalidSizeIsRejected(int width, int height) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> new Bounds(10, 10, width, height));

        assertEquals(ModelingMessages.NODE_SIZE, rejected.reason().key());
    }
}
