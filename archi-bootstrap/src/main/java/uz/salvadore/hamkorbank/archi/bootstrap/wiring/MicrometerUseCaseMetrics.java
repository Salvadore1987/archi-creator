package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Метрики use case'ов из spec/nfr/&lt;bc&gt;.yaml#observability:
 * {@code <bc>_usecase_requests_total{usecase,outcome}},
 * {@code <bc>_usecase_duration_seconds{usecase}},
 * {@code <bc>_usecase_errors_total{usecase,error_code}} — код инварианта в {@code error_code}:
 * по нему алерт {@code ConcurrentSaveConflicts} считает {@code INV-MDL-006}.
 */
public final class MicrometerUseCaseMetrics {

    private final MeterRegistry registry;
    private final String prefix;
    private final Function<Throwable, String> errorCode;

    public MicrometerUseCaseMetrics(MeterRegistry registry, String prefix, Function<Throwable, String> errorCode) {
        this.registry = registry;
        this.prefix = prefix;
        this.errorCode = errorCode;
    }

    public <T> T observe(String useCase, Supplier<T> work) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        try {
            return work.get();
        } catch (RuntimeException e) {
            outcome = "error";
            Counter.builder(prefix + ".usecase.errors").tag("usecase", useCase).tag("error_code", errorCode.apply(e))
                    .register(registry).increment();
            throw e;
        } finally {
            sample.stop(Timer.builder(prefix + ".usecase.duration").tag("usecase", useCase)
                    .serviceLevelObjectives(java.time.Duration.ofMillis(10), java.time.Duration.ofMillis(50),
                            java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(250),
                            java.time.Duration.ofMillis(500), java.time.Duration.ofSeconds(1),
                            java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(5))
                    .register(registry));
            Counter.builder(prefix + ".usecase.requests").tag("usecase", useCase).tag("outcome", outcome)
                    .register(registry).increment();
        }
    }
}
