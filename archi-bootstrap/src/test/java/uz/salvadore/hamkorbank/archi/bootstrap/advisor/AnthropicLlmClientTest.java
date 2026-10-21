package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmListener;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmRequest;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmStream;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmUnavailableException;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

/** Адаптер Anthropic на подменном сервере: события потока, расход при обрыве и предохранитель. */
class AnthropicLlmClientTest {

    private static final String ANSWER = "Сервис карточки клиента реализован системой CRM. ".repeat(8);

    private final FakeAnthropic anthropic = FakeAnthropic.get();
    private final CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
    private final AnthropicLlmClient client = new AnthropicLlmClient(new AiSettings("sk-test", "claude-sonnet-5",
            anthropic.baseUrl(), "medium", Duration.ofSeconds(10), Duration.ofSeconds(2), 0, 1024, 120_000, 0.25,
            5_000_000, 5, 20, ZoneId.of("UTC"), new AiSettings.Breaker(50, Duration.ofSeconds(60), 10,
            Duration.ofSeconds(60))), breakers);
    private final LlmRequest request = new LlmRequest("system", "user", List.of(), 1024);

    @BeforeEach
    void reset() {
        anthropic.reset();
    }

    @Test
    @DisplayName("INV-ADV-006: целый ответ — расход из message_delta")
    void completeStreamReportsUsageFromApi() {
        anthropic.reply(FakeAnthropic.Reply.text(ANSWER).usage(300, 7));
        Recorder listener = new Recorder();

        client.open(request).consume(listener);

        assertThat(listener.text).hasToString(ANSWER);
        assertThat(listener.last()).isEqualTo(new TokenUsage(300, 7));
    }

    @Test
    @DisplayName("INV-ADV-006: поток оборвался после текста — выход списывается не меньше оценки")
    void cutStreamReportsEstimatedOutput() {
        anthropic.reply(FakeAnthropic.Reply.text(ANSWER).usage(300, 7).cutAfter(3));
        Recorder listener = new Recorder();
        LlmStream stream = client.open(request);

        assertThatThrownBy(() -> stream.consume(listener)).isInstanceOf(LlmUnavailableException.class);

        assertThat(listener.text).hasToString(ANSWER);
        assertThat(listener.last().promptTokens()).isEqualTo(300);
        assertThat(listener.last().completionTokens()).isEqualTo(TokenUsage.estimate(ANSWER.length()));
    }

    @Test
    @DisplayName("INV-ADV-006: поток отменён после первого текста — выход списывается не меньше оценки")
    void cancelledStreamReportsEstimatedOutput() {
        anthropic.reply(FakeAnthropic.Reply.text(ANSWER).withText(ANSWER).usage(300, 7).slowly(200));
        LlmStream stream = client.open(request);
        Recorder listener = new Recorder() {
            @Override
            public void text(String delta) {
                super.text(delta);
                stream.cancel();
            }
        };

        assertThatThrownBy(() -> stream.consume(listener)).isInstanceOf(LlmUnavailableException.class);

        assertThat(listener.last().completionTokens()).isGreaterThanOrEqualTo(TokenUsage.estimate(ANSWER.length()));
    }

    @Test
    @DisplayName("UC-ADV-001: окно отказов открывает предохранитель — обращение отклоняется, не доходя до API")
    void failuresOpenTheBreaker() {
        anthropic.failWith(503);
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> client.open(request)).isInstanceOf(LlmUnavailableException.class);
        }

        assertThatThrownBy(() -> client.open(request)).isInstanceOf(LlmUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        assertThat(anthropic.requests()).hasSize(10);
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("UC-ADV-001: оборванный поток — неудача для предохранителя, целый — успех")
    void streamOutcomeIsRecorded() {
        client.open(request).consume(new Recorder());
        anthropic.reply(FakeAnthropic.Reply.text(ANSWER).usage(300, 7).cutAfter(3));
        LlmStream cut = client.open(request);
        assertThatThrownBy(() -> cut.consume(new Recorder())).isInstanceOf(LlmUnavailableException.class);

        assertThat(breaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(breaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("UC-ADV-001: отмена клиентом — не исход: предохранитель её не считает")
    void cancellationIsNotAFailure() {
        for (int i = 0; i < 10; i++) {
            LlmStream stream = client.open(request);
            stream.cancel();
            assertThatThrownBy(() -> stream.consume(new Recorder())).isInstanceOf(LlmUnavailableException.class);
        }

        assertThat(breaker().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker().getMetrics().getNumberOfSuccessfulCalls()).isZero();
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private CircuitBreaker breaker() {
        return breakers.circuitBreaker(AnthropicLlmClient.BREAKER);
    }

    /** События потока, собранные для проверки. */
    private static class Recorder implements LlmListener {

        final StringBuilder text = new StringBuilder();
        final List<TokenUsage> usage = new ArrayList<>();

        @Override
        public void text(String delta) {
            text.append(delta);
        }

        @Override
        public void toolCall(String name, Map<String, Object> input) {
        }

        @Override
        public void usage(TokenUsage cumulative) {
            usage.add(cumulative);
        }

        TokenUsage last() {
            return usage.getLast();
        }
    }
}
