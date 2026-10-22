package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

class StyleOverrideTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4})
    @DisplayName("Выравнивание текста — значения Archi: влево, по центру, вправо")
    void archiAlignmentsAreAccepted(int alignment) {
        assertEquals(alignment, alignment(alignment).textAlignment());
    }

    @Test
    @DisplayName("Выравнивание вне набора Archi отклоняется, как цвет не того формата")
    void unknownAlignmentIsRejected() {
        assertThrows(InvalidValueException.class, () -> alignment(3));
        assertThrows(InvalidValueException.class, () -> alignment(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "red", "#fff", "#gggggg", "ff0000", "#ff00001"})
    @DisplayName("Цвет в стиле — # и шесть шестнадцатеричных цифр")
    void malformedColorIsRejected(String color) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> new StyleOverride(color, null, null, null, null));

        assertEquals(ModelingMessages.COLOR_FORMAT, rejected.reason().key());
    }

    private static StyleOverride alignment(int value) {
        return new StyleOverride(null, null, null, null,
                value);
    }
}
