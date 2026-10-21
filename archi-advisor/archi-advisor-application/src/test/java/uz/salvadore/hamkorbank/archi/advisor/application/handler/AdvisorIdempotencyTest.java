package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

class AdvisorIdempotencyTest {

    private static final Locale RU = Locale.forLanguageTag("ru");

    @Test
    @DisplayName("INV-ADV-003: повтор с тем же ключом отдаёт результат сессии и не обращается к API второй раз")
    void replayDoesNotCallUpstreamTwice() {
        Fixture fixture = new Fixture();
        fixture.script = listener -> {
            listener.usage(new TokenUsage(300, 20));
            listener.toolCall("report_finding", Map.of("severity", "INFO", "code", "NAMING", "message", "m",
                    "targetKind", "ELEMENT", "targetArchiId", "svc", "suggestion", "s"));
        };
        Fixture.Advisor service = fixture.service();
        AdvisorRequest review = AdvisorRequest.review(Fixture.MODEL, null);
        Fixture.Recorded first = new Fixture.Recorded();
        service.ask(Fixture.ALICE, review, "same-key", RU).run(first);

        Fixture.Recorded replay = new Fixture.Recorded();
        service.ask(Fixture.ALICE, review, "same-key", RU).run(replay);

        assertEquals(1, fixture.upstreamCalls.size(), "второго обращения к внешнему API нет");
        assertEquals(first.findings, replay.findings);
        assertEquals(first.meta, replay.meta);
        assertEquals(320, fixture.budgets.values().iterator().next().consumedTokens(), "расход не удвоен");
    }

    @Test
    @DisplayName("INV-ADV-003: тот же ключ с другими параметрами — конфликт")
    void sameKeyDifferentRequestConflicts() {
        Fixture fixture = new Fixture();
        Fixture.Advisor service = fixture.service();
        service.ask(Fixture.ALICE, AdvisorRequest.review(Fixture.MODEL, null), "k", RU)
                .run(new Fixture.Recorded());

        AdvisorDomainException conflict = assertThrows(AdvisorDomainException.class,
                () -> service.ask(Fixture.ALICE, AdvisorRequest.explain(Fixture.MODEL, "crm"), "k", RU));

        assertEquals("INV-ADV-003", conflict.code());
    }

    @Test
    @DisplayName("INV-ADV-003: ключ другого автора — другая сессия")
    void keysAreScopedByRequester() {
        Fixture fixture = new Fixture();
        Fixture.Advisor service = fixture.service();
        AdvisorRequest review = AdvisorRequest.review(Fixture.MODEL, null);
        service.ask(Fixture.ALICE, review, "k", RU).run(new Fixture.Recorded());
        service.ask(Fixture.VIEWER, review, "k", RU).run(new Fixture.Recorded());

        assertEquals(2, fixture.upstreamCalls.size());
    }

    @Test
    @DisplayName("INV-ADV-003: ключ, занятый параллельным обращением, пока читалась модель, — конфликт без потока")
    void concurrentSameKeyDoesNotOpenSecondStream() {
        Fixture fixture = new Fixture();
        AdvisorRequest review = AdvisorRequest.review(Fixture.MODEL, null);
        fixture.duringRead = () -> fixture.sessions.putIfAbsent("alice/k", AdvisorSession.request(
                AdvisorSessionId.of("00000000-0000-7000-8000-0000000000cc"), Fixture.WORKSPACE, review, "k", "alice",
                Fixture.NOW));

        AdvisorDomainException conflict = assertThrows(AdvisorDomainException.class,
                () -> fixture.service().ask(Fixture.ALICE, review, "k", RU));

        assertEquals("INV-ADV-003", conflict.code());
        assertEquals(AdvisorMessages.SESSION_IN_PROGRESS, conflict.reason().key());
        assertEquals(0, fixture.upstreamCalls.size(), "второго потока у внешнего API нет");
    }

    @Test
    @DisplayName("INV-ADV-003: повтор ключа отказа по бюджету — тот же отказ с датой сброса")
    void replayedBudgetRejectionKeepsResetDate() {
        Fixture fixture = new Fixture();
        fixture.limit = 50L;
        Fixture.Advisor service = fixture.service();
        AdvisorRequest review = AdvisorRequest.review(Fixture.MODEL, null);
        service.ask(Fixture.ALICE, review, "spend", RU).run(new Fixture.Recorded());
        AdvisorDomainException first = assertThrows(AdvisorDomainException.class,
                () -> service.ask(Fixture.ALICE, review, "over", RU));

        AdvisorDomainException replay = assertThrows(AdvisorDomainException.class,
                () -> service.ask(Fixture.ALICE, review, "over", RU));

        assertEquals("BUDGET_EXHAUSTED", replay.code());
        assertEquals(first.details(), replay.details());
        assertEquals(first.reason(), replay.reason());
    }

    @Test
    @DisplayName("INV-ADV-003: повтор ключа отказа по частоте — тот же отказ с ожиданием")
    void replayedRateRejectionKeepsWait() {
        Fixture fixture = new Fixture();
        Fixture.Advisor service = fixture.service();
        AdvisorRequest review = AdvisorRequest.review(Fixture.MODEL, null);
        for (int i = 0; i < fixture.policy.userRequestsPerMinute(); i++) {
            service.ask(Fixture.ALICE, review, "ok-" + i, RU).run(new Fixture.Recorded());
        }
        AdvisorDomainException first = assertThrows(AdvisorDomainException.class,
                () -> service.ask(Fixture.ALICE, review, "late", RU));

        AdvisorDomainException replay = assertThrows(AdvisorDomainException.class,
                () -> service.ask(Fixture.ALICE, review, "late", RU));

        assertEquals("RATE_LIMITED", replay.code());
        assertEquals(Map.of("retryAfterSeconds", 60L), replay.details());
        assertEquals(first.reason(), replay.reason());
    }
}
