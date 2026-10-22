package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Наблюдение за use case'ами — одна точка для метрик и журнала всех трёх контекстов.
 *
 * <p>Метрики:
 * {@code <bc>_usecase_requests_total{usecase,outcome}},
 * {@code <bc>_usecase_duration_seconds{usecase}},
 * {@code <bc>_usecase_errors_total{usecase,error_code}} — код инварианта в {@code error_code}:
 * по нему алерт {@code ConcurrentSaveConflicts} считает {@code INV-MDL-006}.
 *
 * <p>Журнал: начало и исход сценария записью {@code INFO}, у отказа — код без трассы стека: отказ сценария —
 * результат, а неожиданный сбой со стеком пишет обработчик отказов. На время сценария его имя лежит в MDC
 * под ключом {@code usecase}; вложенный сценарий возвращает имя внешнего.
 */
public final class MicrometerUseCaseMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerUseCaseMetrics.class);
    private static final String MDC_KEY = "usecase";

    private final MeterRegistry registry;
    private final String prefix;
    private final Function<Throwable, String> errorCode;

    public MicrometerUseCaseMetrics(MeterRegistry registry, String prefix, Function<Throwable, String> errorCode) {
        this.registry = registry;
        this.prefix = prefix;
        this.errorCode = errorCode;
    }

    /**
     * Выполняет сценарий и пишет счётчик запросов с исходом, длительность и, при отказе, ошибку с её кодом;
     * начало и исход — в журнал. Исключение пробрасывается дальше без изменений.
     */
    public <T> T observe(String useCase, Supplier<T> work) {
        String outer = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, useCase);
        LOG.info("{} {} started", prefix, useCase);
        Timer.Sample sample = Timer.start(registry);
        String failure = null;
        try {
            return work.get();
        } catch (RuntimeException e) { // NOPMD - счётчик отказов считает любой отказ и пробрасывает его
            failure = errorCode.apply(e);
            Counter.builder(prefix + ".usecase.errors").tag("usecase", useCase).tag("error_code", failure)
                    .register(registry).increment();
            throw e;
        } finally {
            long millis = TimeUnit.NANOSECONDS.toMillis(sample.stop(Timer.builder(prefix + ".usecase.duration")
                    .tag("usecase", useCase)
                    .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(50),
                            Duration.ofMillis(100), Duration.ofMillis(250),
                            Duration.ofMillis(500), Duration.ofSeconds(1),
                            Duration.ofSeconds(2), Duration.ofSeconds(5))
                    .register(registry)));
            Counter.builder(prefix + ".usecase.requests").tag("usecase", useCase)
                    .tag("outcome", failure == null ? "success" : "error").register(registry).increment();
            if (failure == null) {
                LOG.info("{} {} succeeded in {} ms", prefix, useCase, millis);
            } else {
                LOG.info("{} {} refused with {} in {} ms", prefix, useCase, failure, millis);
            }
            if (outer == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, outer);
            }
        }
    }
}
