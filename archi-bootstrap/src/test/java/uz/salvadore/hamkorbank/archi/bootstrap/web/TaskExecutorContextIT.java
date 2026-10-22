package uz.salvadore.hamkorbank.archi.bootstrap.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;

/** Исполнитель, в котором читается поток помощника, несёт контекст журнала запроса. */
class TaskExecutorContextIT extends IntegrationTest {

    @Autowired
    @Qualifier("applicationTaskExecutor")
    private AsyncTaskExecutor executor;

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    @DisplayName("задача applicationTaskExecutor пишет журнал с traceId запроса")
    void traceIdReachesExecutor() throws Exception {
        MDC.put("traceId", "0af7651916cd43dd8448eb211c80319c");
        CompletableFuture<String> seen = executor.submitCompletable(() -> MDC.get("traceId"));
        assertThat(seen.get(10, TimeUnit.SECONDS)).isEqualTo("0af7651916cd43dd8448eb211c80319c");
    }
}
