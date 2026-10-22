package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;

class AuditEntryTest {

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"REQUESTED", "STREAMING"})
    @DisplayName("Запись аудита фиксирует только исход: незавершённое состояние — отказ")
    void nonTerminalOutcomeIsRejected(SessionStatus outcome) {
        InvalidValueException refused = assertThrows(InvalidValueException.class,
                () -> new AuditEntry(Sessions.audit(), "alice", AdvisorFunction.REVIEW, TokenUsage.NONE, outcome,
                        null, Sessions.NOW));

        assertEquals(AdvisorCodes.INVALID_INPUT, refused.code());
        assertEquals(Message.of(AdvisorMessages.AUDIT_OUTCOME_NOT_TERMINAL), refused.reason());
    }
}
