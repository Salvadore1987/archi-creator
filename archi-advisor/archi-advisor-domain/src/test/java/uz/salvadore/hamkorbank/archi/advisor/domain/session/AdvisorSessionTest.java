package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;

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

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidIdempotencyKeys")
    @DisplayName("Обращение без ключа идемпотентности или с ключом длиннее предела не создаётся")
    void invalidIdempotencyKeyIsRejected(String key) {
        AdvisorRequest request = AdvisorRequest.review(Sessions.MODEL, null);

        InvalidValueException refused = assertThrows(InvalidValueException.class,
                () -> AdvisorSession.request(AdvisorSessionId.next(Sessions.UUIDS), Sessions.WORKSPACE, request, key,
                        "alice", Sessions.NOW));

        assertEquals(AdvisorCodes.INVALID_INPUT, refused.code());
        assertEquals(Message.of(AdvisorMessages.IDEMPOTENCY_KEY_INVALID, AdvisorSession.IDEMPOTENCY_KEY_MAX),
                refused.reason());
    }

    @Test
    @DisplayName("Отрицательный размер контекста — поток не начинается, сессия остаётся в REQUESTED")
    void negativeContextSizeIsRejected() {
        AdvisorSession session = Sessions.review();

        InvalidValueException refused = assertThrows(InvalidValueException.class,
                () -> session.startStreaming(-1, false));

        assertEquals(AdvisorCodes.INVALID_INPUT, refused.code());
        assertEquals(Message.of(AdvisorMessages.USAGE_NEGATIVE), refused.reason());
        assertEquals(SessionStatus.REQUESTED, session.status());
    }

    @Test
    @DisplayName("Функция плана не завершается без плана")
    void planFunctionRequiresPlan() {
        AdvisorSession plan = streaming(AdvisorRequest.plan(Sessions.MODEL, "Платёжный шлюз"));

        InvalidValueException refused = assertThrows(InvalidValueException.class,
                () -> plan.complete(TokenUsage.NONE, null, null, Sessions.audit(), Sessions.NOW));

        assertEquals(AdvisorCodes.INVALID_INPUT, refused.code());
        assertEquals(Message.of(AdvisorMessages.PLAN_REQUIRED), refused.reason());
        assertEquals(SessionStatus.STREAMING, plan.status());
    }

    private static Stream<String> invalidIdempotencyKeys() {
        return Stream.of("  ", "k".repeat(AdvisorSession.IDEMPOTENCY_KEY_MAX + 1));
    }

    private static AdvisorSession streaming(AdvisorRequest request) {
        AdvisorSession session = Sessions.requested(request);
        session.startStreaming(10, false);
        return session;
    }
}
