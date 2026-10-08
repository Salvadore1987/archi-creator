package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;

/**
 * Метрики interchange на Micrometer (spec/nfr/interchange.yaml#observability.metrics).
 * Потери выгрузки считаются и нулём: алерт {@code RoundTripLossDetected} обязан видеть
 * ряд, даже пока потерь нет.
 */
public final class InterchangeMicrometerMetrics implements InterchangeMetrics {

    private final MeterRegistry registry;
    private final MicrometerUseCaseMetrics useCases;
    private final Map<UUID, AtomicLong> opaque = new ConcurrentHashMap<>();

    public InterchangeMicrometerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.useCases = new MicrometerUseCaseMetrics(registry, "interchange", ErrorCodes::of);
    }

    @Override
    public <T> T observe(String useCase, Supplier<T> work) {
        return useCases.observe(useCase, work);
    }

    @Override
    public void importFinished(String outcome, boolean strictMode, List<ImportFinding> findings) {
        Counter.builder("interchange.import.sessions").tag("outcome", outcome)
                .tag("strict_mode", String.valueOf(strictMode)).register(registry).increment();
        findings.forEach(f -> Counter.builder("interchange.import.findings").tag("severity", f.severity().name())
                .tag("code", f.code()).register(registry).increment());
    }

    @Override
    public void opaqueObjects(UUID modelId, int count) {
        opaque.computeIfAbsent(modelId, id -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder("interchange.opaque.objects", value, AtomicLong::doubleValue)
                    .tag("model_id", id.toString()).register(registry);
            return value;
        }).set(count);
    }

    @Override
    public void exportLosses(String format, int entries) {
        Counter.builder("interchange.export.loss.entries").tag("format", format).tag("object_kind", "any")
                .register(registry).increment(entries);
    }
}
