package uz.salvadore.hamkorbank.archi.advisor.domain.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.TokenBudgetId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.RejectionReason;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

class TokenBudgetTest {

    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    private static final UuidV7 UUIDS = new UuidV7(Clock.fixed(NOW, ZoneOffset.UTC));
    private static final BudgetPeriod OCTOBER = BudgetPeriod.parse("2026-10");

    private static TokenBudget budget(long limit) {
        return TokenBudget.open(TokenBudgetId.next(UUIDS), WorkspaceId.of(UUIDS.next()), OCTOBER, limit, true);
    }

    @Test
    @DisplayName("INV-ADV-005: исчерпанный бюджет и выключенный помощник отклоняют новые сессии")
    void exhaustedBudgetRejectsNewSessions() {
        TokenBudget budget = budget(1000);
        budget.record(new TokenUsage(900, 100), NOW);

        assertEquals(BudgetStatus.EXHAUSTED, budget.status());
        assertEquals(Optional.of(RejectionReason.BUDGET_EXHAUSTED), budget.admission());

        TokenBudget disabled = budget(1000);
        disabled.toggle(false);
        assertEquals(Optional.of(RejectionReason.ADVISOR_DISABLED), disabled.admission());
    }

    @Test
    @DisplayName("INV-ADV-005: 80 % — предупреждение, но не остановка")
    void warningDoesNotBlockSessions() {
        TokenBudget budget = budget(1000);
        budget.record(new TokenUsage(800, 0), NOW);

        assertEquals(BudgetStatus.WARNING, budget.status());
        assertEquals(Optional.empty(), budget.admission());
        TokenBudgetThresholdReached event = assertInstanceOf(TokenBudgetThresholdReached.class,
                budget.pullEvents().getFirst());
        assertEquals(80, event.thresholdPercent());

        budget.record(new TokenUsage(10, 0), NOW);
        assertTrue(budget.pullEvents().isEmpty(), "порог уведомляет один раз, а не на каждое обращение");
    }

    @Test
    @DisplayName("INV-ADV-006: расход только растёт и не уходит в минус")
    void consumptionIsMonotonic() {
        TokenBudget budget = budget(10_000);
        budget.record(new TokenUsage(100, 50), NOW);
        budget.record(TokenUsage.NONE, NOW);
        budget.record(new TokenUsage(1, 1), NOW);

        assertEquals(152, budget.consumedTokens());
        assertThrows(InvalidValueException.class, () -> new TokenUsage(-1, 0));
        assertThrows(InvalidValueException.class, () -> TokenBudget.restore(TokenBudgetId.next(UUIDS),
                WorkspaceId.of(UUIDS.next()), OCTOBER, 100, -5, 80, true, 0));
    }

    @Test
    @DisplayName("INV-ADV-006: неуспешная сессия тоже расходует бюджет")
    void failedSessionStillConsumes() {
        TokenBudget budget = budget(1000);
        TokenUsage spentBeforeFailure = new TokenUsage(400, 37);
        budget.record(spentBeforeFailure, NOW);

        assertEquals(437, budget.consumedTokens());
    }

    @Test
    @DisplayName("INV-ADV-006: повышение лимита выводит из EXHAUSTED, расход при этом не меняется")
    void raisingLimitLeavesExhausted() {
        TokenBudget budget = budget(1000);
        budget.record(new TokenUsage(1000, 0), NOW);
        assertInstanceOf(TokenBudgetExhausted.class, budget.pullEvents().getFirst());

        budget.changeLimit(1200, NOW);
        assertEquals(BudgetStatus.WARNING, budget.status());
        budget.changeLimit(10_000, NOW);
        assertEquals(BudgetStatus.OK, budget.status());
        assertEquals(1000, budget.consumedTokens());
        assertThrows(InvalidValueException.class, () -> budget.changeLimit(0, NOW));
    }

    @Test
    @DisplayName("Период — календарный месяц, сброс с первого числа следующего")
    void periodIsCalendarMonth() {
        BudgetPeriod period = BudgetPeriod.containing(Instant.parse("2026-12-31T20:00:00Z"), ZoneId.of("Asia/Tashkent"));

        assertEquals("2027-01", period.value(), "по поясу учёта это уже январь");
        assertEquals(LocalDate.of(2027, 2, 1), period.resetsAt());
        assertThrows(InvalidValueException.class, () -> BudgetPeriod.parse("2026-13"));
    }
}
