package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorMetrics;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.RejectionReason;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.SessionStatus;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;
import uz.salvadore.hamkorbank.archi.bootstrap.wiring.ErrorCodes;
import uz.salvadore.hamkorbank.archi.bootstrap.wiring.MicrometerUseCaseMetrics;

/**
 * Метрики помощника на Micrometer: исходы сессий, время до первого токена, расход
 * и заполнение бюджета. Ни содержимое контекста, ни текст ответа в метки не попадают.
 */
public final class AdvisorMicrometerMetrics implements AdvisorMetrics {

    private final MeterRegistry registry;
    private final MicrometerUseCaseMetrics useCases;
    private final Map<String, AtomicReference<Double>> utilization = new ConcurrentHashMap<>();

    public AdvisorMicrometerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.useCases = new MicrometerUseCaseMetrics(registry, "advisor", ErrorCodes::of);
    }

    @Override
    public <T> T observe(String useCase, Supplier<T> work) {
        return useCases.observe(useCase, work);
    }

    @Override
    public void session(AdvisorFunction function, SessionStatus outcome, Optional<RejectionReason> reason) {
        Counter.builder("advisor.sessions").tag("function", function.name()).tag("outcome", outcome.name())
                .tag("rejection_reason", reason.map(Enum::name).orElse("none")).register(registry).increment();
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
        DistributionSummary.builder("advisor.tokens.consumed").tag("workspace", workspace)
                .tag("function", function.name()).tag("kind", "prompt").register(registry)
                .record(usage.promptTokens());
        DistributionSummary.builder("advisor.tokens.consumed").tag("workspace", workspace)
                .tag("function", function.name()).tag("kind", "completion").register(registry)
                .record(usage.completionTokens());
    }

    @Override
    public void budget(TokenBudget budget) {
        String key = budget.workspaceId() + "/" + budget.period();
        utilization.computeIfAbsent(key, k -> {
            AtomicReference<Double> value = new AtomicReference<>(0.0);
            Gauge.builder("advisor.budget.utilization.ratio", value, AtomicReference::get)
                    .tag("workspace", budget.workspaceId().toString()).tag("period", budget.period().value())
                    .register(registry);
            return value;
        }).set(budget.utilization());
    }

    @Override
    public void contextTruncated(AdvisorFunction function) {
        Counter.builder("advisor.context.truncated").tag("function", function.name()).register(registry).increment();
    }
}
