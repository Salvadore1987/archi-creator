package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UseCaseMetrics;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.validation.Severity;

/** Метрики modeling на Micrometer (spec/nfr/modeling.yaml#observability.metrics). */
public final class ModelingMicrometerMetrics implements UseCaseMetrics {

    private final MeterRegistry registry;
    private final MicrometerUseCaseMetrics useCases;
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public ModelingMicrometerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.useCases = new MicrometerUseCaseMetrics(registry, "modeling", ErrorCodes::of);
    }

    @Override
    public <T> T observe(String useCase, Supplier<T> work) {
        return useCases.observe(useCase, work);
    }

    @Override
    public void lockWait(ModelId modelId, Duration remaining) {
        DistributionSummary.builder("modeling.lock.wait").baseUnit("seconds").tag("model_id", modelId.toString())
                .register(registry).record(Math.max(0, remaining.toMillis()) / 1000.0);
    }

    @Override
    public void modelSize(ModelId modelId, int elements) {
        gauge("modeling.model.elements", Map.of("model_id", modelId.toString())).set(elements);
    }

    @Override
    public void validationFindings(ModelId modelId, Map<String, Long> bySeverity) {
        for (Severity severity : Severity.values()) {
            gauge("modeling.validation.findings", Map.of("model_id", modelId.toString(), "severity", severity.name()))
                    .set(bySeverity.getOrDefault(severity.name(), 0L));
        }
    }

    private AtomicLong gauge(String name, Map<String, String> tags) {
        return gauges.computeIfAbsent(name + tags, key -> {
            AtomicLong value = new AtomicLong();
            Gauge.Builder<AtomicLong> builder = Gauge.builder(name, value, AtomicLong::doubleValue);
            tags.forEach(builder::tag);
            builder.register(registry);
            return value;
        });
    }
}
