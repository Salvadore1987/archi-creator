package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;

class AdvisorSessionTest {

    @Test
    @DisplayName("INV-ADV-001: у каждой терминальной сессии ровно одна запись аудита — и у отклонённой, и у неуспешной")
    void auditEntryExistsForEveryOutcome() {
        Map<SessionStatus, Consumer<AdvisorSession>> outcomes = Map.of(
                SessionStatus.REJECTED, s -> s.reject(RejectionReason.BUDGET_EXHAUSTED, Sessions.audit(), Sessions.NOW),
                SessionStatus.FAILED, s -> s.fail(TokenUsage.NONE, Sessions.audit(), Sessions.NOW),
                SessionStatus.COMPLETED, s -> {
                    s.startStreaming(100, false);
                    s.complete(new TokenUsage(100, 20), Optional.empty(), Optional.empty(), Sessions.audit(),
                            Sessions.NOW);
                });
        outcomes.forEach((outcome, finish) -> {
            AdvisorSession session = Sessions.review();
            assertTrue(session.auditEntry().isEmpty(), "до терминального состояния записи нет");
            finish.accept(session);

            AuditEntry entry = session.auditEntry().orElseThrow();
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

        AuditEntry entry = session.auditEntry().orElseThrow();
        assertEquals(Optional.of(RejectionReason.ADVISOR_DISABLED), entry.rejectionReason());
        assertEquals(0, entry.usage().total());
    }

    @Test
    @DisplayName("INV-ADV-003: повтор отличается от конфликта отпечатком параметров")
    void fingerprintDistinguishesReplayFromConflict() {
        AdvisorSession session = Sessions.review();

        assertTrue(session.sameRequest(AdvisorRequest.review(Sessions.MODEL, Optional.empty())));
        assertTrue(!session.sameRequest(AdvisorRequest.explain(Sessions.MODEL, "id-1")));
    }

    @Test
    @DisplayName("Время первого токена фиксируется один раз")
    void firstTokenIsRecordedOnce() {
        AdvisorSession session = Sessions.review();
        session.startStreaming(10, false);
        session.firstToken(Sessions.NOW);
        session.firstToken(Sessions.NOW.plusSeconds(5));

        assertEquals(Optional.of(Sessions.NOW), session.firstTokenAt());
    }
}
