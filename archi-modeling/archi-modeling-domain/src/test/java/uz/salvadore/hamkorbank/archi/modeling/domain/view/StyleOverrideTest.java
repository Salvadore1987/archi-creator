package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;

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

    private static StyleOverride alignment(int value) {
        return new StyleOverride(null, null, null, null,
                value);
    }
}
