package uz.salvadore.hamkorbank.archi.advisor.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudgetExhausted;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSessionCompleted;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.RejectionReason;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.SessionStatus;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

class AdvisorServiceTest {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final BudgetPeriod OCTOBER = BudgetPeriod.parse("2026-10");

    private static AdvisorRequest review() {
        return AdvisorRequest.review(Fixture.MODEL, Optional.empty());
    }

    @Test
    @DisplayName("INV-ADV-005: выключенный помощник отказывает до внешнего API, отказ записан с аудитом")
    void disabledAdvisorRejectsBeforeUpstream() {
        Fixture fixture = new Fixture();
        fixture.enabled = false;

        AdvisorException refused = assertThrows(AdvisorException.class,
                () -> fixture.service().ask(Fixture.ALICE, review(), "k1", RU));

        assertEquals("ADVISOR_DISABLED", refused.code());
        assertEquals(Failure.FORBIDDEN, refused.failure());
        assertTrue(fixture.upstreamCalls.isEmpty(), "внешний API не вызывался");
        AdvisorSession session = fixture.onlySession();
        assertEquals(SessionStatus.REJECTED, session.status());
        assertTrue(session.auditEntry().isPresent());
        assertTrue(fixture.events.stream().anyMatch(AdvisorSessionCompleted.class::isInstance));
    }

    @Test
    @DisplayName("INV-ADV-005: исчерпанный бюджет — 429 с датой сброса, без обращения к API")
    void exhaustedBudgetRejectsWithResetDate() {
        Fixture fixture = new Fixture();
        fixture.limit = Optional.of(100L);
        AdvisorService service = fixture.service();
        service.ask(Fixture.ALICE, review(), "k1", RU).run(new Fixture.Recorded());
        assertTrue(fixture.events.stream().anyMatch(TokenBudgetExhausted.class::isInstance));

        AdvisorException refused = assertThrows(AdvisorException.class,
                () -> service.ask(Fixture.ALICE, review(), "k2", RU));

        assertEquals("BUDGET_EXHAUSTED", refused.code());
        assertEquals(Failure.TOO_MANY_REQUESTS, refused.failure());
        assertEquals("2026-11-01", refused.details().get("resetsAt"));
        assertEquals(1, fixture.upstreamCalls.size());
    }

    @Test
    @DisplayName("INV-ADV-007: частота сверх предела — отказ RATE_LIMITED с паузой, а не очередь")
    void rateLimitRejects() {
        Fixture fixture = new Fixture();
        fixture.policy = new AdvisorPolicy(120_000, 0.25, 5_000_000, 4096, 2, 20, ZoneId.of("UTC"));
        AdvisorService service = fixture.service();
        service.ask(Fixture.ALICE, review(), "k1", RU).run(new Fixture.Recorded());
        service.ask(Fixture.ALICE, review(), "k2", RU).run(new Fixture.Recorded());

        AdvisorException refused = assertThrows(AdvisorException.class,
                () -> service.ask(Fixture.ALICE, review(), "k3", RU));

        assertEquals("RATE_LIMITED", refused.code());
        assertTrue((Long) refused.details().get("retryAfterSeconds") > 0);
        assertEquals(RejectionReason.RATE_LIMITED,
                fixture.sessions.get("alice/k3").rejectionReason().orElseThrow());
    }

    @Test
    @DisplayName("INV-ADV-007: контекст, который даже усечённым покрывает меньше четверти области, — 413")
    void tooLargeContextIsRejected() {
        Fixture fixture = new Fixture();
        fixture.policy = new AdvisorPolicy(30, 0.9, 5_000_000, 4096, 5, 20, ZoneId.of("UTC"));

        AdvisorException refused = assertThrows(AdvisorException.class,
                () -> fixture.service().ask(Fixture.ALICE, review(), "k1", RU));

        assertEquals("CONTEXT_TOO_LARGE", refused.code());
        assertEquals(Failure.TOO_LARGE, refused.failure());
        assertTrue(fixture.upstreamCalls.isEmpty());
    }

    @Test
    @DisplayName("INV-ADV-007: усечённый контекст помечен в запросе к модели и в итоге сессии")
    void truncatedContextIsFlaggedInPromptAndMeta() {
        Fixture fixture = new Fixture();
        fixture.policy = new AdvisorPolicy(60, 0.25, 5_000_000, 4096, 5, 20, ZoneId.of("UTC"));
        Fixture.Recorded recorded = new Fixture.Recorded();

        fixture.service().ask(Fixture.ALICE, review(), "k1", RU).run(recorded);

        assertTrue(fixture.upstreamCalls.getFirst().user().contains("\"truncated\":true"));
        assertTrue(recorded.meta.contextTruncated());
    }

    @Test
    @DisplayName("INV-ADV-008: находка с целью вне модели отбрасывается, остальные идут потоком")
    void findingsWithoutResolvableTargetAreDropped() {
        Fixture fixture = new Fixture();
        fixture.script = listener -> {
            listener.usage(new TokenUsage(200, 0));
            listener.toolCall("report_finding", Map.of("severity", "WARNING", "code", "orphan element",
                    "message", "Не размещён ни на одном представлении", "targetKind", "ELEMENT",
                    "targetArchiId", "crm", "suggestion", "Разместите на обзорном представлении"));
            listener.toolCall("report_finding", Map.of("severity", "ERROR", "code", "X", "message", "m",
                    "targetKind", "ELEMENT", "targetArchiId", "invented", "suggestion", ""));
            listener.usage(new TokenUsage(200, 40));
        };
        Fixture.Recorded recorded = new Fixture.Recorded();

        fixture.service().ask(Fixture.ALICE, review(), "k1", Locale.ENGLISH).run(recorded);

        assertEquals(1, recorded.findings.size());
        assertEquals("ORPHAN_ELEMENT", recorded.findings.getFirst().code());
        assertEquals("CRM", recorded.findings.getFirst().target().name(), "имя берётся из модели, а не из ответа");
        assertEquals(SessionStatus.COMPLETED, recorded.meta.outcome());
        assertEquals(1, fixture.onlySession().findings().size());
        assertTrue(fixture.upstreamCalls.getFirst().system().endsWith("en"), "язык ответа — язык запроса");
    }

    @Test
    @DisplayName("INV-ADV-006: обрыв потока завершает сессию неуспехом, но израсходованное списывается")
    void brokenStreamStillConsumesBudget() {
        Fixture fixture = new Fixture();
        fixture.script = listener -> {
            listener.usage(new TokenUsage(500, 0));
            listener.text("нача");
            listener.usage(new TokenUsage(500, 7));
            throw new Fixture.Broken();
        };
        Fixture.Recorded recorded = new Fixture.Recorded();

        fixture.service().ask(Fixture.ALICE, review(), "k1", RU).run(recorded);

        assertEquals(SessionStatus.FAILED, recorded.meta.outcome());
        assertEquals(507, fixture.budgets.get(OCTOBER).consumedTokens());
        assertEquals(SessionStatus.FAILED, fixture.onlySession().auditEntry().orElseThrow().outcome());
        assertTrue(fixture.opened.getFirst().cancelled);
    }

    @Test
    @DisplayName("INV-ADV-006: клиент ушёл посреди потока — поток у внешнего API прерван, расход учтён")
    void clientDisconnectCancelsUpstream() {
        Fixture fixture = new Fixture();
        Fixture.Recorded gone = new Fixture.Recorded() {
            @Override
            public void token(String delta) {
                throw new IllegalStateException();
            }
        };

        fixture.service().ask(Fixture.ALICE, review(), "k1", RU).run(gone);

        assertTrue(fixture.opened.getFirst().cancelled);
        assertEquals(SessionStatus.FAILED, fixture.onlySession().status());
        assertEquals(100, fixture.budgets.get(OCTOBER).consumedTokens());
    }

    @Test
    @DisplayName("Недоступный внешний API — 503 до потока, сессия FAILED с аудитом")
    void upstreamUnavailableBeforeStream() {
        Fixture fixture = new Fixture();
        fixture.upstreamDown = true;

        AdvisorException refused = assertThrows(AdvisorException.class,
                () -> fixture.service().ask(Fixture.ALICE, review(), "k1", RU));

        assertEquals("ADVISOR_UNAVAILABLE", refused.code());
        assertEquals(SessionStatus.FAILED, fixture.onlySession().status());
        assertTrue(fixture.onlySession().auditEntry().isPresent());
    }

    @Test
    @DisplayName("Новое обращение автора отменяет его предыдущий поток")
    void newRequestCancelsPreviousStream() {
        Fixture fixture = new Fixture();
        AdvisorService service = fixture.service();
        AdvisorStream first = service.ask(Fixture.ALICE, review(), "k1", RU);

        service.ask(Fixture.ALICE, review(), "k2", RU);

        assertTrue(fixture.opened.getFirst().cancelled);
        assertFalse(fixture.opened.get(1).cancelled);
        first.run(new Fixture.Recorded());
    }

    @Test
    @DisplayName("Роль: план — только архитектору, ревью доступно и наблюдателю")
    void rolesFollowTheTable() {
        Fixture fixture = new Fixture();
        AdvisorService service = fixture.service();

        AdvisorException denied = assertThrows(AdvisorException.class,
                () -> service.ask(Fixture.VIEWER, AdvisorRequest.plan(Fixture.MODEL, "Платежи"), "k1", RU));
        assertEquals("ADV_ACCESS_DENIED", denied.code());
        assertTrue(fixture.sessions.isEmpty(), "до проверки роли сессия не заводится");

        service.ask(Fixture.VIEWER, review(), "k2", RU).run(new Fixture.Recorded());
        assertEquals(SessionStatus.COMPLETED, fixture.onlySession().status());
    }

    @Test
    @DisplayName("Удалённая модель — отказ MODEL_DELETED")
    void deletedModelIsRejected() {
        Fixture fixture = new Fixture();
        fixture.deleted = true;

        AdvisorException refused = assertThrows(AdvisorException.class,
                () -> fixture.service().ask(Fixture.ALICE, review(), "k1", RU));
        assertEquals("MODEL_DELETED", refused.code());
        assertEquals(List.of(), fixture.upstreamCalls);
    }
}
