package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;

class AdvisorSessionTest {

    @Test
    @DisplayName("INV-ADV-001: у каждой терминальной сессии ровно одна запись аудита — и у отклонённой, и у неуспешной")
    void auditEntryExistsForEveryOutcome() {
        Map<SessionStatus, Consumer<AdvisorSession>> outcomes = Map.of(
                SessionStatus.REJECTED, s -> s.reject(RejectionReason.BUDGET_EXHAUSTED, Sessions.audit(), Sessions.NOW),
                SessionStatus.FAILED, s -> s.fail(TokenUsage.NONE, Sessions.audit(), Sessions.NOW),
                SessionStatus.COMPLETED, s -> {
                    s.startStreaming(100, false);
                    s.complete(new TokenUsage(100, 20), null, null, Sessions.audit(),
                            Sessions.NOW);
                });
        outcomes.forEach((outcome, finish) -> {
            AdvisorSession session = Sessions.review();
            assertNull(session.auditEntry(), "до терминального состояния записи нет");
            finish.accept(session);

            AuditEntry entry = session.auditEntry();
            assertEquals(outcome, entry.outcome());
            assertEquals("alice", entry.actor());
            assertEquals(AdvisorFunction.REVIEW, entry.function());
            assertEquals(session.usage(), entry.usage());

            List<DomainEvent> events = session.pullEvents();
            assertEquals(1, events.size(), "событие завершения — на каждый исход");
            assertEquals(outcome, ((AdvisorSessionCompletedEvent) events.getFirst()).outcome());
        });
    }

    @Test
    @DisplayName("INV-ADV-001: отказ записывается с причиной и нулевым расходом")
    void rejectionIsAuditedWithReason() {
        AdvisorSession session = Sessions.review();
        session.reject(RejectionReason.ADVISOR_DISABLED, Sessions.audit(), Sessions.NOW);

        AuditEntry entry = session.auditEntry();
        assertEquals(RejectionReason.ADVISOR_DISABLED, entry.rejectionReason());
        assertEquals(0, entry.usage().total());
    }

    @Test
    @DisplayName("INV-ADV-003: повтор отличается от конфликта отпечатком параметров")
    void fingerprintDistinguishesReplayFromConflict() {
        AdvisorSession session = Sessions.review();

        assertTrue(session.sameRequest(AdvisorRequest.review(Sessions.MODEL, null)));
        assertTrue(!session.sameRequest(AdvisorRequest.explain(Sessions.MODEL, "id-1")));
    }

    @Test
    @DisplayName("Время первого токена фиксируется один раз")
    void firstTokenIsRecordedOnce() {
        AdvisorSession session = Sessions.review();
        session.startStreaming(10, false);
        session.firstToken(Sessions.NOW);
        session.firstToken(Sessions.NOW.plusSeconds(5));

        assertEquals(Sessions.NOW, session.firstTokenAt());
    }

    @Test
    @DisplayName("результат — своей функции: ответ обязателен подсказке, чужой план или ответ — отказ")
    void resultBelongsToFunction() {
        AdvisorAnswer answer = new AdvisorAnswer("ответ", List.of());
        ModelPlan plan = new ModelPlan(List.of(), List.of(), null);

        AdvisorSession explain = streaming(AdvisorRequest.explain(Sessions.MODEL, "id-e1"));
        assertThrows(InvalidValueException.class,
                () -> explain.complete(TokenUsage.NONE, null, null, Sessions.audit(), Sessions.NOW));
        assertThrows(InvalidValueException.class,
                () -> explain.complete(TokenUsage.NONE, plan, answer, Sessions.audit(), Sessions.NOW));

        AdvisorSession review = streaming(AdvisorRequest.review(Sessions.MODEL, null));
        assertThrows(InvalidValueException.class,
                () -> review.complete(TokenUsage.NONE, null, answer, Sessions.audit(), Sessions.NOW));
        assertEquals(SessionStatus.STREAMING, review.status(), "отказ ничего не меняет");

        explain.complete(TokenUsage.NONE, null, answer, Sessions.audit(), Sessions.NOW);
        assertEquals(answer, explain.answer());
    }

    private static AdvisorSession streaming(AdvisorRequest request) {
        AdvisorSession session = Sessions.requested(request);
        session.startStreaming(10, false);
        return session;
    }
}
