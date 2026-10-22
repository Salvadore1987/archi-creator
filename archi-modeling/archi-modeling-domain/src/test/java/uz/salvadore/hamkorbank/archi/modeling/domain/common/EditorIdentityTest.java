package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EditorIdentityTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Пользователь без subject не действует в модели")
    void blankSubjectIsRejected(String subject) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> EditorIdentity.of(subject, Role.ARCHITECT));

        assertEquals(ModelingMessages.EDITOR_WITHOUT_SUBJECT, rejected.reason().key());
    }
}
