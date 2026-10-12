package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Подменный Anthropic API на уровне HTTP: {@code POST /v1/messages} отвечает потоком
 * SSE в формате Messages API. Сеть не нужна, а клиент SDK и разбор потока —
 * настоящие. Ответ задаёт тест; запросы запоминаются для проверок.
 */
public final class FakeAnthropic {

    private static final FakeAnthropic INSTANCE = new FakeAnthropic();

    private final HttpServer server;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger messageIds = new AtomicInteger();
    private volatile Reply reply = Reply.text("Готово.");
    private volatile int status = 200;

    private FakeAnthropic() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/v1/messages", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Recorded(exchange.getRequestHeaders().getFirst("x-api-key"), body));
            if (status != 200) {
                byte[] error = ("{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"down\"}}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, error.length);
                exchange.getResponseBody().write(error);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (String frame : reply.frames("msg_" + messageIds.incrementAndGet())) {
                    out.write(frame.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    pause(reply.frameDelayMs);
                }
            }
        });
        server.start();
    }

    public static FakeAnthropic get() {
        return INSTANCE;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset() {
        requests.clear();
        reply = Reply.text("Готово.");
        status = 200;
    }

    public void reply(Reply next) {
        reply = next;
    }

    /** Все запросы отвечают ошибкой с этим статусом — API недоступен. */
    public void failWith(int httpStatus) {
        status = httpStatus;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<Recorded> requests() {
        return List.copyOf(requests);
    }

    /** Запрос к API: ключ из заголовка и тело как есть. */
    public record Recorded(String apiKey, String body) {
    }

    /** Ответ модели: текст и вызовы инструментов, расход. */
    public static final class Reply {

        private final List<String> blocks = new ArrayList<>();
        private long inputTokens = 120;
        private long outputTokens = 30;
        private long frameDelayMs;

        public static Reply text(String text) {
            return new Reply().withText(text);
        }

        public Reply withText(String text) {
            blocks.add("T" + text);
            return this;
        }

        /** Вызов инструмента с готовым JSON входа. */
        public Reply withTool(String name, String inputJson) {
            blocks.add("U" + name + "\u0000" + inputJson);
            return this;
        }

        /** Пауза после каждого кадра — модель пишет не мгновенно. */
        public Reply slowly(long millis) {
            this.frameDelayMs = millis;
            return this;
        }

        public Reply usage(long input, long output) {
            this.inputTokens = input;
            this.outputTokens = output;
            return this;
        }

        List<String> frames(String messageId) {
            List<String> frames = new ArrayList<>();
            frames.add(frame("message_start", "{\"type\":\"message_start\",\"message\":{\"id\":\"" + messageId
                    + "\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-sonnet-5\",\"content\":[],"
                    + "\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":" + inputTokens
                    + ",\"output_tokens\":1}}}"));
            for (int i = 0; i < blocks.size(); i++) {
                String block = blocks.get(i);
                if (block.startsWith("T")) {
                    frames.add(frame("content_block_start", "{\"type\":\"content_block_start\",\"index\":" + i
                            + ",\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"));
                    frames.add(frame("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":" + i
                            + ",\"delta\":{\"type\":\"text_delta\",\"text\":" + quote(block.substring(1)) + "}}"));
                } else {
                    String[] parts = block.substring(1).split("\u0000", 2);
                    frames.add(frame("content_block_start", "{\"type\":\"content_block_start\",\"index\":" + i
                            + ",\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_" + i + "\",\"name\":\""
                            + parts[0] + "\",\"input\":{}}}"));
                    frames.add(frame("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":" + i
                            + ",\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":" + quote(parts[1])
                            + "}}"));
                }
                frames.add(frame("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":" + i + "}"));
            }
            frames.add(frame("message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\","
                    + "\"stop_sequence\":null},\"usage\":{\"output_tokens\":" + outputTokens + "}}"));
            frames.add(frame("message_stop", "{\"type\":\"message_stop\"}"));
            return frames;
        }

        private static String frame(String event, String data) {
            return "event: " + event + "\ndata: " + data + "\n\n";
        }

        private static String quote(String value) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        }
    }
}
