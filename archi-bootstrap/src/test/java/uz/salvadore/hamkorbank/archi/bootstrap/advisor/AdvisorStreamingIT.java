package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;

/**
 * Поток помощника через настоящий Tomcat, а не MockMvc: MockMvc собирает ответ целиком
 * и не отличит поток от ответа одним куском. Так и прошёл незамеченным дефект —
 * {@code StreamingResponseBody} в Spring 7 не сбрасывал кадры до конца ответа.
 * Профиль {@code dev}: автор — заглушка {@code ARCHITECT}, токен не нужен.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AdvisorStreamingIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void advisor(DynamicPropertyRegistry registry) {
        registry.add("archi.ai.api-key", () -> AdvisorApiTest.API_KEY);
        registry.add("archi.ai.base-url", () -> FakeAnthropic.get().baseUrl());
        registry.add("archi.ai.max-retries", () -> 0);
    }

    @Test
    @DisplayName("NFR-04: первые токены уходят клиенту, пока модель ещё пишет, а не по концу ответа")
    void firstTokensArriveBeforeTheAnswerEnds() throws Exception {
        FakeAnthropic anthropic = FakeAnthropic.get();
        anthropic.reset();
        anthropic.reply(FakeAnthropic.Reply.text("Первый фрагмент. ").withText("Второй фрагмент. ")
                .withText("Третий фрагмент.").slowly(400));
        jdbc.update("update workspace set ai_enabled = true, ai_monthly_token_limit = null");
        HttpClient client = HttpClient.newHttpClient();
        String created = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/models"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "stream-model-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Поток\"}"))
                .build(), HttpResponse.BodyHandlers.ofString()).body();
        UUID model = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));

        long started = System.nanoTime();
        HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/ai/review"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "stream-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofString("{\"modelId\":\"" + model + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofInputStream());

        long firstToken = -1;
        long last = -1;
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                long at = (System.nanoTime() - started) / 1_000_000;
                if (line.startsWith("event:token") && firstToken < 0) {
                    firstToken = at;
                }
                last = at;
            }
        }

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/event-stream"));
        assertThat(firstToken).as("первый токен пришёл").isNotNegative();
        assertThat(last - firstToken).as("между первым токеном и концом потока — время, пока модель пишет")
                .isGreaterThan(1_000);
    }
}
