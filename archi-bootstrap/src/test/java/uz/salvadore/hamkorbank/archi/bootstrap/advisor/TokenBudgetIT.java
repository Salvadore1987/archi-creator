package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenBudgetIT extends AdvisorApiTest {

    @Test
    @DisplayName("INV-ADV-006: параллельные сессии не теряют расход друг друга")
    void concurrentSessionsDoNotLoseConsumption() throws Exception {
        UUID model = lockedModel(architect(), "Параллельный расход");
        anthropic.reply(FakeAnthropic.Reply.text("Ответ.").usage(100, 11));
        int sessions = 8;
        try (ExecutorService pool = Executors.newFixedThreadPool(sessions)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < sessions; i++) {
                String subject = "parallel-" + i;
                String key = "parallel-key-" + i;
                calls.add(() -> ask("review", as(subject, "ARCHITECT"), key,
                        "{\"modelId\":\"" + model + "\"}").status());
            }
            for (Future<Integer> status : pool.invokeAll(calls)) {
                assertThat(status.get()).isEqualTo(200);
            }
        }

        assertThat(jdbc.queryForObject("select consumed_tokens from token_budget", Long.class))
                .isEqualTo(sessions * 111L);
        assertThat(jdbc.queryForObject("select count(*) from token_budget", Long.class)).isEqualTo(1L);
    }
}
