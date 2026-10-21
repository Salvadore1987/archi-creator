package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.TokenBudgetId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

/** Метрики помощника в реестре: заполнение бюджета по месяцам и расход счётчиком. */
class AdvisorMicrometerMetricsTest {

    private static final TimeBasedEpochGenerator IDS = Generators.timeBasedEpochGenerator();
    private static final BudgetPeriod OCTOBER = BudgetPeriod.of(YearMonth.of(2026, 10));
    private static final BudgetPeriod NOVEMBER = BudgetPeriod.of(YearMonth.of(2026, 11));

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AdvisorMicrometerMetrics metrics = new AdvisorMicrometerMetrics(registry);
    private final WorkspaceId workspace = WorkspaceId.of(IDS.generate());

    @Test
    @DisplayName("FR-53: новый месяц снимает датчик прошлого — его последнее значение не держит алерт")
    void newPeriodReplacesThePreviousGauge() {
        metrics.budget(budget(OCTOBER, 950));
        metrics.budget(budget(NOVEMBER, 100));

        assertThat(registry.find("advisor.budget.utilization.ratio").gauges()).singleElement()
                .satisfies(gauge -> {
                    assertThat(gauge.getId().getTag("period")).isEqualTo(NOVEMBER.value());
                    assertThat(gauge.value()).isEqualTo(0.1);
                });
    }

    @Test
    @DisplayName("FR-53: запоздалый расход прошлого месяца не возвращает его датчик")
    void lateBudgetOfThePreviousPeriodIsIgnored() {
        metrics.budget(budget(NOVEMBER, 100));
        metrics.budget(budget(OCTOBER, 950));

        Gauge gauge = registry.get("advisor.budget.utilization.ratio").gauge();
        assertThat(gauge.getId().getTag("period")).isEqualTo(NOVEMBER.value());
        assertThat(gauge.value()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("FR-53: расход в том же месяце обновляет тот же датчик")
    void samePeriodUpdatesTheGauge() {
        metrics.budget(budget(OCTOBER, 100));
        metrics.budget(budget(OCTOBER, 300));

        assertThat(registry.get("advisor.budget.utilization.ratio").gauge().value()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("FR-53: расход токенов — счётчик, как объявляет спецификация метрик")
    void tokensAreCounted() {
        metrics.tokens(AdvisorFunction.REVIEW, workspace.toString(), new TokenUsage(300, 7));
        metrics.tokens(AdvisorFunction.REVIEW, workspace.toString(), new TokenUsage(200, 3));

        assertThat(registry.get("advisor.tokens.consumed").tag("kind", "prompt").counter().count()).isEqualTo(500);
        assertThat(registry.get("advisor.tokens.consumed").tag("kind", "completion").counter().count())
                .isEqualTo(10);
    }

    private TokenBudget budget(BudgetPeriod period, long consumed) {
        return TokenBudget.restore(TokenBudgetId.of(IDS.generate()), workspace, period, 1000, consumed,
                TokenBudget.DEFAULT_WARNING_PERCENT, true, 0);
    }
}
