package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;

class AdvisorSessionStateMachineTest {

    @Test
    @DisplayName("INV-ADV-002: отказ возможен только до потока, то есть до расхода токенов")
    void rejectionHappensBeforeStreaming() {
        AdvisorSession streaming = Sessions.review();
        streaming.startStreaming(100, false);

        IllegalSessionTransitionException late = assertThrows(IllegalSessionTransitionException.class,
                () -> streaming.reject(RejectionReason.BUDGET_EXHAUSTED, Sessions.audit(), Sessions.NOW));
        assertEquals("INV-ADV-002", late.code());
        assertEquals(List.of(Message.of(AdvisorMessages.STATUS_STREAMING), Message.of(AdvisorMessages.COMMAND_REJECT)),
                late.reason().args(), "состояние и команда — ключами, а не именами констант");
        assertEquals(SessionStatus.STREAMING, streaming.status());
    }

    @Test
    @DisplayName("INV-ADV-002: до потока сессия отклоняется и ничего не тратит")
    void requestedSessionIsRejected() {
        AdvisorSession requested = Sessions.review();

        requested.reject(RejectionReason.BUDGET_EXHAUSTED, Sessions.audit(), Sessions.NOW);

        assertEquals(SessionStatus.REJECTED, requested.status());
        assertEquals(0, requested.usage().total(), "отклонённая сессия ничего не потратила");
    }

    @Test
    @DisplayName("INV-ADV-002: к завершённой сессии находка не добавляется")
    void completedSessionTakesNoFindings() {
        AdvisorSession session = completed();

        assertThrows(IllegalSessionTransitionException.class, () -> session.addFinding(Sessions.finding("id-1")));
    }

    @Test
    @DisplayName("INV-ADV-002: завершённая сессия не переводится в FAILED")
    void completedSessionDoesNotFail() {
        AdvisorSession session = completed();

        assertThrows(IllegalSessionTransitionException.class,
                () -> session.fail(TokenUsage.NONE, Sessions.audit(), Sessions.NOW));
    }

    @Test
    @DisplayName("INV-ADV-002: завершённая сессия не открывает поток заново")
    void completedSessionDoesNotStreamAgain() {
        AdvisorSession session = completed();

        assertThrows(IllegalSessionTransitionException.class, () -> session.startStreaming(1, false));
    }

    @Test
    @DisplayName("INV-ADV-002: FAILED достижим из REQUESTED")
    void failIsReachableFromRequested() {
        AdvisorSession requested = Sessions.review();

        requested.fail(TokenUsage.NONE, Sessions.audit(), Sessions.NOW);

        assertEquals(SessionStatus.FAILED, requested.status());
    }

    @Test
    @DisplayName("INV-ADV-002: FAILED достижим из STREAMING, израсходованное до обрыва учтено")
    void failIsReachableFromStreaming() {
        AdvisorSession streaming = Sessions.review();
        streaming.startStreaming(10, true);
        streaming.fail(new TokenUsage(10, 3), Sessions.audit(), Sessions.NOW);
        assertEquals(SessionStatus.FAILED, streaming.status());
        assertEquals(13, streaming.usage().total(), "израсходованное до обрыва учтено");
    }

    @Test
    @DisplayName("Находки бывают только у ревью")
    void findingsOnlyForReview() {
        AdvisorSession explain = Sessions.requested(AdvisorRequest.explain(Sessions.MODEL, "id-1"));
        explain.startStreaming(10, false);

        assertThrows(IllegalSessionTransitionException.class, () -> explain.addFinding(Sessions.finding("id-1")));
    }

    private static AdvisorSession completed() {
        AdvisorSession session = Sessions.review();
        session.startStreaming(100, false);
        session.complete(new TokenUsage(1, 1), null, null, Sessions.audit(), Sessions.NOW);
        return session;
    }
}
