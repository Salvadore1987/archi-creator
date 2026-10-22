package uz.salvadore.hamkorbank.archi.advisor.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequesterTest {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("Автор обращения без subject из JWT не создаётся")
    void blankSubjectIsRejected(String subject) {
        InvalidValueException refused = assertThrows(InvalidValueException.class,
                () -> Requester.of(subject, Role.ARCHITECT));

        assertEquals(AdvisorCodes.INVALID_INPUT, refused.code());
        assertEquals(Message.of(AdvisorMessages.REQUESTER_WITHOUT_SUBJECT), refused.reason());
    }
}
