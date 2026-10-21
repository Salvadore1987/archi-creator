package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/** Адаптер Anthropic на подменном сервере: события потока и расход при обрыве. */
class AnthropicLlmClientTest {

    private static final String ANSWER = "Сервис карточки клиента реализован системой CRM. ".repeat(8);

    private final FakeAnthropic anthropic = FakeAnthropic.get();
    private final AnthropicLlmClient client = new AnthropicLlmClient(new AiSettings("sk-test", "claude-sonnet-5",
            anthropic.baseUrl(), "medium", Duration.ofSeconds(10), 0, 1024, 120_000, 0.25, 5_000_000, 5, 20,
            ZoneId.of("UTC")));
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
