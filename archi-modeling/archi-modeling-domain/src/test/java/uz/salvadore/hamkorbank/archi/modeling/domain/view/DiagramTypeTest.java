package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

class DiagramTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "archimate", ":Group", "archimate:", "a:b:c", "1archimate:Group", "archimate:Gr oup"})
    @DisplayName("Тип объекта представления — префикс и имя через двоеточие")
    void malformedTypeIsRejected(String value) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class, () -> new DiagramType(value));

        assertEquals(ModelingMessages.DIAGRAM_TYPE_FORMAT, rejected.reason().key());
    }
}
