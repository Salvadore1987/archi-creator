package uz.salvadore.hamkorbank.archi.bootstrap.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    @DisplayName("задача видит контекст журнала того потока, где её отдали исполнителю")
    void taskSeesSubmitterContext() throws InterruptedException {
        MDC.put("traceId", "abc");
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seen.set(MDC.getCopyOfContextMap()));
        MDC.clear();
        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();
        assertThat(seen.get()).containsEntry("traceId", "abc");
    }

    @Test
    @DisplayName("после задачи у потока исполнителя прежний контекст, а не контекст задачи")
    void workerContextRestored() {
        MDC.put("traceId", "submitter");
        Runnable decorated = decorator.decorate(() -> MDC.put("modelId", "inside"));
        MDC.clear();
        MDC.put("worker", "own");
        decorated.run();
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("worker", "own"));
    }

    @Test
    @DisplayName("без контекста у отправителя задача идёт с пустым контекстом")
    void emptyContext() {
        Runnable decorated = decorator.decorate(() -> assertThat(MDC.get("traceId")).isNull());
        MDC.put("traceId", "stale");
        decorated.run();
        assertThat(MDC.get("traceId")).isEqualTo("stale");
    }
}
