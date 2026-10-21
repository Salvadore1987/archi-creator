package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
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
import java.io.EOFException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
 * <p>Содержимое запроса и ответа не логируется: в журнал идёт только класс сбоя.
 */
public final class AnthropicLlmClient implements LlmPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final AiSettings settings;
    private final AnthropicClient client;

    public AnthropicLlmClient(AiSettings settings) {
        this.settings = settings;
        this.client = settings.configured()
                ? AnthropicOkHttpClient.builder()
                        .apiKey(settings.apiKey())
                        .baseUrl(settings.baseUrl())
                        .timeout(settings.timeout())
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
        try {
            return new Stream(client.messages().createStreaming(params.build()));
        } catch (AnthropicException | UncheckedIOException e) {
            throw new LlmUnavailableException(e);
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
        private final Map<Long, String> toolNames = new HashMap<>();
        private final Map<Long, StringBuilder> toolInputs = new HashMap<>();
        private long input;
        private long output;
        private long outputChars;
        private boolean stopped;

        Stream(StreamResponse<RawMessageStreamEvent> response) {
            this.response = response;
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
            listener.usage(new TokenUsage(input, Math.max(output, TokenUsage.estimate(outputChars))));
            return new LlmUnavailableException(cause);
        }

        @Override
        public void cancel() {
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
