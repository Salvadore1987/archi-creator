package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorMetrics;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.RejectionReason;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.SessionStatus;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;
import uz.salvadore.hamkorbank.archi.bootstrap.wiring.ErrorCodes;
import uz.salvadore.hamkorbank.archi.bootstrap.wiring.MicrometerUseCaseMetrics;

/**
 * Метрики помощника на Micrometer: исходы сессий, время до первого токена, расход
 * и заполнение бюджета. Ни содержимое контекста, ни текст ответа в метки не попадают.
 *
 * <p>Заполнение бюджета — один датчик на пространство, с меткой текущего месяца: с новым месяцем
 * датчик прошлого снимается, иначе он висел бы с последним значением и держал алерт заполнения.
 */
public final class AdvisorMicrometerMetrics implements AdvisorMetrics {

    private final MeterRegistry registry;
    private final MicrometerUseCaseMetrics useCases;
    private final Map<WorkspaceId, Utilization> utilization = new ConcurrentHashMap<>();

    private record Utilization(BudgetPeriod period, AtomicReference<Double> value, Gauge gauge) {
    }

    public AdvisorMicrometerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.useCases = new MicrometerUseCaseMetrics(registry, "advisor", ErrorCodes::of);
    }

    @Override
    public <T> T observe(String useCase, Supplier<T> work) {
        return useCases.observe(useCase, work);
    }

    @Override
    public void session(AdvisorFunction function, SessionStatus outcome, @Nullable RejectionReason reason) {
        Counter.builder("advisor.sessions").tag("function", function.name()).tag("outcome", outcome.name())
                .tag("rejection_reason", reason == null ? "none" : reason.name()).register(registry).increment();
    }

    @Override
    public void firstToken(AdvisorFunction function, Duration sinceRequest) {
        Timer.builder("advisor.first.token").tag("function", function.name())
                .serviceLevelObjectives(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2),
                        Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(10))
                .register(registry).record(sinceRequest);
    }

    @Override
    public void tokens(AdvisorFunction function, String workspace, TokenUsage usage) {
        Counter.builder("advisor.tokens.consumed").tag("workspace", workspace)
                .tag("function", function.name()).tag("kind", "prompt").register(registry)
                .increment(usage.promptTokens());
        Counter.builder("advisor.tokens.consumed").tag("workspace", workspace)
                .tag("function", function.name()).tag("kind", "completion").register(registry)
                .increment(usage.completionTokens());
    }

    @Override
    public void budget(TokenBudget budget) {
        utilization.compute(budget.workspaceId(), (workspace, current) -> {
            if (current != null && current.period().month().isAfter(budget.period().month())) {
                return current;
            }
            if (current != null && current.period().equals(budget.period())) {
                current.value().set(budget.utilization());
                return current;
            }
            if (current != null) {
                registry.remove(current.gauge());
            }
            AtomicReference<Double> value = new AtomicReference<>(budget.utilization());
            Gauge gauge = Gauge.builder("advisor.budget.utilization.ratio", value, AtomicReference::get)
                    .tag("workspace", workspace.toString()).tag("period", budget.period().value())
                    .register(registry);
            return new Utilization(budget.period(), value, gauge);
        });
    }

    @Override
    public void contextTruncated(AdvisorFunction function) {
        Counter.builder("advisor.context.truncated").tag("function", function.name()).register(registry).increment();
    }
}
