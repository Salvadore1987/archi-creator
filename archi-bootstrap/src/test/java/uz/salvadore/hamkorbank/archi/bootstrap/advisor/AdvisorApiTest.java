package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/**
 * Основа интеграционных тестов помощника: Anthropic API подменён на уровне HTTP
 * ({@link FakeAnthropic}), помощник включён, сессии, аудит и бюджеты очищены.
 * Окна частоты широкие — тесты разных классов делят автора и пространство; частоту
 * проверяет {@code AdvisorRateLimitIT} со своим окном.
 */
@TestPropertySource(properties = "archi.ai.user-requests-per-minute=1000")
abstract class AdvisorApiTest extends ApiTest {

    static final String API_KEY = "sk-ant-test-key-never-leaks-0042";

    protected final FakeAnthropic anthropic = FakeAnthropic.get();

    @DynamicPropertySource
    static void advisor(DynamicPropertyRegistry registry) {
        registry.add("archi.ai.api-key", () -> API_KEY);
        registry.add("archi.ai.base-url", () -> FakeAnthropic.get().baseUrl());
        registry.add("archi.ai.max-retries", () -> 0);
        registry.add("archi.ai.workspace-requests-per-minute", () -> 1000);
    }

    @BeforeEach
    void cleanAdvisor() {
        anthropic.reset();
        jdbc.update("delete from ai_audit_log");
        jdbc.update("delete from advisor_session");
        jdbc.update("delete from token_budget");
        jdbc.update("update workspace set ai_enabled = true, ai_monthly_token_limit = null");
    }

    /** Отправить запрос помощнику и, если открылся поток, дочитать его до конца. */
    protected Response ask(String path, RequestPostProcessor who, String key, String body) throws Exception {
        ResultActions sent = mvc.perform(post("/api/v1/ai/" + path).with(who)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
        MvcResult result = sent.andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result.getAsyncResult(20_000);
            MvcResult done = mvc.perform(asyncDispatch(result)).andReturn();
            return new Response(done.getResponse().getStatus(),
                    done.getResponse().getContentAsString(StandardCharsets.UTF_8),
                    done.getResponse().getHeader("Retry-After"));
        }
        return new Response(result.getResponse().getStatus(),
                result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                result.getResponse().getHeader("Retry-After"));
    }

    protected String archiIdOf(UUID elementId) {
        return jdbc.queryForObject("select archi_id from element where id = ?", String.class, elementId);
    }

    /** Ответ: статус, тело (поток SSE или problem+json), заголовок паузы. */
    protected record Response(int status, String body, String retryAfter) {

        /** Кадры потока заданного типа — их JSON по порядку. */
        List<String> frames(String event) {
            List<String> found = new ArrayList<>();
            String[] lines = body.split("\n");
            for (int i = 0; i + 1 < lines.length; i++) {
                if (lines[i].replace("event: ", "event:").equals("event:" + event)
                        && lines[i + 1].startsWith("data:")) {
                    found.add(lines[i + 1].substring("data:".length()).strip());
                }
            }
            return found;
        }

        String meta() {
            List<String> meta = frames("meta");
            assertThat(meta).as("кадр meta последним").hasSize(1);
            return meta.getFirst();
        }
    }
}
