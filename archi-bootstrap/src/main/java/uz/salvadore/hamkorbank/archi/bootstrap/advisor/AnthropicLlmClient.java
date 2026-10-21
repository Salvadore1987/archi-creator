package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.Timeout;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.InputJsonDelta;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageDeltaUsage;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextDelta;
import com.anthropic.models.messages.Tool;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.EOFException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmListener;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmPort;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmRequest;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmStream;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmUnavailableException;
import uz.salvadore.hamkorbank.archi.advisor.application.port.ToolSpec;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

/**
 * Порт языковой модели на официальном Java SDK Anthropic. Запрос уходит сразу
 * с потоковым ответом: первые токены видны пользователю, пока модель пишет остальное.
 *
 * <p>SDK повторяет запрос до первого байта ответа (сеть, 429, 5xx); после открытия
 * потока повторов нет — пользователь уже видит часть ответа. Инструменты — строгие:
 * вход вызова приходит валидным по схеме и разбирается целиком на конце блока.
 *
 * <p>Предохранитель считает исход каждого обращения: отказ API, обрыв потока и медленное открытие
 * потока — неудача, отмена клиентом — не исход вовсе. Пока он открыт, обращение отклоняется сразу,
 * не дожидаясь соединения и повторов SDK.
 *
 * <p>Содержимое запроса и ответа не логируется: в журнал идёт только класс сбоя.
 */
public final class AnthropicLlmClient implements LlmPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    /** Имя предохранителя — метка {@code name} его метрик. */
    public static final String BREAKER = "anthropic_api";

    private final AiSettings settings;
    private final AnthropicClient client;
    private final CircuitBreaker breaker;

    public AnthropicLlmClient(AiSettings settings, CircuitBreakerRegistry breakers) {
        this.settings = settings;
        this.breaker = breakers.circuitBreaker(BREAKER, breakerConfig(settings.circuitBreaker()));
        this.client = settings.configured()
                ? AnthropicOkHttpClient.builder()
                        .apiKey(settings.apiKey())
                        .baseUrl(settings.baseUrl())
                        .timeout(Timeout.builder().connect(settings.connectTimeout()).request(settings.timeout())
                                .build())
                        .maxRetries(settings.maxRetries())
                        .build()
                : null;
    }

    @Override
    public LlmStream open(LlmRequest request) {
        if (client == null) {
            throw new LlmUnavailableException(new IllegalStateException());
        }
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(settings.model())
                .maxTokens(request.maxTokens())
                .system(request.system())
                .addUserMessage(request.user())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(settings.effort())).build());
        request.tools().forEach(tool -> params.addTool(tool(tool)));
        if (!breaker.tryAcquirePermission()) {
            throw new LlmUnavailableException(CallNotPermittedException.createCallNotPermittedException(breaker));
        }
        long started = breaker.getCurrentTimestamp();
        try {
            StreamResponse<RawMessageStreamEvent> response = client.messages().createStreaming(params.build());
            return new Stream(response, new Outcome(breaker, breaker.getCurrentTimestamp() - started));
        } catch (AnthropicException | UncheckedIOException e) {
            breaker.onError(breaker.getCurrentTimestamp() - started, breaker.getTimestampUnit(), e);
            throw new LlmUnavailableException(e);
        }
    }

    /** Окно по счёту обращений; медленные обращения открывают предохранитель той же долей, что отказы. */
    private static CircuitBreakerConfig breakerConfig(AiSettings.Breaker settings) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.slidingWindowSize())
                .failureRateThreshold(settings.failureRateThreshold())
                .slowCallRateThreshold(settings.failureRateThreshold())
                .slowCallDurationThreshold(settings.slowCallThreshold())
                .waitDurationInOpenState(settings.waitInOpenState())
                .build();
    }

    /**
     * Исход обращения для предохранителя — ровно один: успех, неудача или отмена. Длительность —
     * время до открытия потока: длинный ответ модели — не медленное обращение.
     */
    private static final class Outcome {

        private final CircuitBreaker breaker;
        private final long openedIn;
        private final AtomicBoolean settled = new AtomicBoolean();

        Outcome(CircuitBreaker breaker, long openedIn) {
            this.breaker = breaker;
            this.openedIn = openedIn;
        }

        void success() {
            if (settled.compareAndSet(false, true)) {
                breaker.onSuccess(openedIn, breaker.getTimestampUnit());
            }
        }

        void failure(Throwable cause) {
            if (settled.compareAndSet(false, true)) {
                breaker.onError(openedIn, breaker.getTimestampUnit(), cause);
            }
        }

        void cancelled() {
            if (settled.compareAndSet(false, true)) {
                breaker.releasePermission();
            }
        }
    }

    private static Tool tool(ToolSpec spec) {
        Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
        @SuppressWarnings("unchecked")
        Map<String, Object> declared = (Map<String, Object>) spec.inputSchema().get("properties");
        declared.forEach((name, schema) -> properties.putAdditionalProperty(name, JsonValue.from(schema)));
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) spec.inputSchema().get("required");
        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(properties.build())
                        .required(required)
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    /**
     * Открытый поток: события SDK — в события порта. Выход Anthropic сообщает в начале ответа
     * и в его конце; поток, оборванный или отменённый посередине, получает расход выхода не меньше
     * оценки по пришедшим символам — иначе обрыв перед концом списывал бы только вход. Поток,
     * кончившийся без {@code message_stop}, — тоже обрыв: ответ получен не целиком.
     */
    private static final class Stream implements LlmStream {

        private final StreamResponse<RawMessageStreamEvent> response;
        private final Outcome outcome;
        private final Map<Long, String> toolNames = new HashMap<>();
        private final Map<Long, StringBuilder> toolInputs = new HashMap<>();
        private long input;
        private long output;
        private long outputChars;
        private boolean stopped;

        Stream(StreamResponse<RawMessageStreamEvent> response, Outcome outcome) {
            this.response = response;
            this.outcome = outcome;
        }

        @Override
        public void consume(LlmListener listener) {
            try (response) {
                Iterator<RawMessageStreamEvent> events = response.stream().iterator();
                while (events.hasNext()) {
                    accept(events.next(), listener);
                }
            } catch (AnthropicException | UncheckedIOException e) {
                throw broken(listener, e);
            }
            if (!stopped) {
                throw broken(listener, new EOFException());
            }
            outcome.success();
        }

        private void accept(RawMessageStreamEvent event, LlmListener listener) {
            if (event.isMessageStart()) {
                input = event.asMessageStart().message().usage().inputTokens();
                output = event.asMessageStart().message().usage().outputTokens();
                listener.usage(new TokenUsage(input, output));
            } else if (event.isContentBlockStart()) {
                RawContentBlockStartEvent start = event.asContentBlockStart();
                start.contentBlock().toolUse().ifPresent(use -> {
                    toolNames.put(start.index(), use.name());
                    toolInputs.put(start.index(), new StringBuilder());
                });
            } else if (event.isContentBlockDelta()) {
                delta(event.asContentBlockDelta().index(), event.asContentBlockDelta().delta(), listener);
            } else if (event.isContentBlockStop()) {
                long index = event.asContentBlockStop().index();
                String name = toolNames.remove(index);
                StringBuilder json = toolInputs.remove(index);
                if (name != null) {
                    listener.toolCall(name, parse(json));
                }
            } else if (event.isMessageDelta()) {
                MessageDeltaUsage usage = event.asMessageDelta().usage();
                input = usage.inputTokens().orElse(input);
                output = usage.outputTokens();
                listener.usage(new TokenUsage(input, output));
            } else if (event.isMessageStop()) {
                stopped = true;
            }
        }

        private void delta(long index, RawContentBlockDelta delta, LlmListener listener) {
            String text = delta.text().map(TextDelta::text).orElse("");
            if (!text.isEmpty()) {
                outputChars += text.length();
                listener.text(text);
            }
            String json = delta.inputJson().map(InputJsonDelta::partialJson).orElse("");
            if (!json.isEmpty()) {
                outputChars += json.length();
                toolInputs.computeIfAbsent(index, k -> new StringBuilder()).append(json);
            }
        }

        /** Сообщить израсходованное к обрыву и вернуть отказ для сценария. */
        private LlmUnavailableException broken(LlmListener listener, Exception cause) {
            outcome.failure(cause);
            listener.usage(new TokenUsage(input, Math.max(output, TokenUsage.estimate(outputChars))));
            return new LlmUnavailableException(cause);
        }

        @Override
        public void cancel() {
            outcome.cancelled();
            response.close();
        }

        private static Map<String, Object> parse(StringBuilder json) {
            try {
                return json == null || json.isEmpty() ? Map.of() : JSON.readValue(json.toString(), MAP);
            } catch (JacksonException unreadable) {
                // Вход не разобрался — вызов пропускается: разбор результата отбросит пустую находку.
                return Map.of();
            }
        }
    }
}
