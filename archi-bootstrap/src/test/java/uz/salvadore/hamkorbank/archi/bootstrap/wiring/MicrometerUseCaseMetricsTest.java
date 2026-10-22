package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class MicrometerUseCaseMetricsTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(MicrometerUseCaseMetrics.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerUseCaseMetrics metrics =
            new MicrometerUseCaseMetrics(registry, "modeling", failure -> "INV-MDL-006");

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    @DisplayName("начало и успешный исход сценария — две записи INFO, имя сценария — в MDC на время работы")
    void startAndSuccess() {
        AtomicReference<String> inside = new AtomicReference<>();
        String result = metrics.observe("SaveModel", () -> {
            inside.set(MDC.get("usecase"));
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(inside.get()).isEqualTo("SaveModel");
        assertThat(MDC.get("usecase")).isNull();
        assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.INFO, Level.INFO);
        assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("modeling SaveModel started");
        assertThat(appender.list.get(1).getFormattedMessage()).startsWith("modeling SaveModel succeeded in ");
        assertThat(registry.counter("modeling.usecase.requests", "usecase", "SaveModel", "outcome", "success")
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("отказ — исход с кодом, без трассы стека; исключение уходит дальше без изменений")
    void refusal() {
        IllegalStateException refusal = new IllegalStateException("locked");
        assertThatThrownBy(() -> metrics.observe("SaveModel", () -> {
            throw refusal;
        })).isSameAs(refusal);
        ILoggingEvent outcome = appender.list.getLast();
        assertThat(outcome.getLevel()).isEqualTo(Level.INFO);
        assertThat(outcome.getFormattedMessage()).startsWith("modeling SaveModel refused with INV-MDL-006 in ");
        assertThat(outcome.getThrowableProxy()).isNull();
        assertThat(registry.counter("modeling.usecase.errors", "usecase", "SaveModel", "error_code", "INV-MDL-006")
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("вложенный сценарий возвращает в MDC имя внешнего")
    void nestedRestoresOuter() {
        AtomicReference<String> afterInner = new AtomicReference<>();
        metrics.observe("ExportModel", () -> {
            metrics.observe("GetModelContent", () -> null);
            afterInner.set(MDC.get("usecase"));
            return null;
        });
        assertThat(afterInner.get()).isEqualTo("ExportModel");
    }
}
