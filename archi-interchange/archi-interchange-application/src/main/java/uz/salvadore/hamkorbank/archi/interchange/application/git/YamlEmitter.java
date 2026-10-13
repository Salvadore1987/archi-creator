package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Запись дерева значений в YAML побайтово предсказуемо: порядок ключей — порядок
 * вставки, отступ два пробела, LF, без якорей и без переноса длинных строк.
 *
 * <p>Значения — строки, упорядоченные отображения, списки и {@link Flow}. Строка
 * выходит простым скаляром, если её нельзя прочесть иначе; многострочная — блоком
 * {@code |}, и тогда переводы строк внутри остаются переводами строк в файле;
 * всё прочее — в двойных кавычках с экранированием. Чтение — {@link GitYamlReader}:
 * все скаляры там строки, поэтому {@code 24} и {@code "24"} читаются одинаково,
 * и кавычки ставятся только там, где без них ломается синтаксис.
 */
final class YamlEmitter {

    private static final int STEP = 2;
    private static final Pattern FLOW_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern FLOW_VALUE = Pattern.compile("-?[A-Za-z0-9_.#]+");
    private static final String PLAIN_FIRST_FORBIDDEN = "-?:,[]{}#&*!|>'\"%@`";

    /** Короткое отображение одной строкой: {@code {x: 24, y: 60}}. Только если все значения просты. */
    record Flow(Map<String, String> entries) {
    }

    private final StringBuilder out = new StringBuilder(4096);

    static byte[] document(List<String> comments, Map<String, Object> root) {
        YamlEmitter emitter = new YamlEmitter();
        comments.forEach(c -> emitter.out.append("# ").append(c).append('\n'));
        emitter.mapping(root, 0);
        return emitter.out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Подходит ли отображение для записи одной строкой. */
    static boolean flowable(Map<String, String> entries) {
        return !entries.isEmpty() && entries.entrySet().stream()
                .allMatch(e -> FLOW_KEY.matcher(e.getKey()).matches() && FLOW_VALUE.matcher(e.getValue()).matches());
    }

    private void mapping(Map<String, Object> map, int indent) {
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                pad(indent);
            }
            first = false;
            out.append(inline(entry.getKey())).append(':');
            value(entry.getValue(), indent);
        }
    }

    /** Значение после {@code ключ:} или {@code -}; {@code parent} — отступ узла-владельца. */
    @SuppressWarnings("unchecked")
    private void value(Object value, int parent) {
        switch (value) {
            case String text -> scalar(text, parent);
            case Flow flow -> out.append(' ').append(flow(flow)).append('\n');
            case Map<?, ?> map when map.isEmpty() -> out.append(" {}\n");
            case Map<?, ?> map -> {
                out.append('\n');
                pad(parent + STEP);
                mapping((Map<String, Object>) map, parent + STEP);
            }
            case List<?> list when list.isEmpty() -> out.append(" []\n");
            case List<?> list -> {
                out.append('\n');
                sequence(list, parent + STEP);
            }
            default -> throw new IllegalStateException(value.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    private void sequence(List<?> list, int indent) {
        for (Object item : list) {
            pad(indent).append('-');
            if (item instanceof Map<?, ?> map && !map.isEmpty()) {
                out.append(' ');
                mapping((Map<String, Object>) map, indent + STEP);
            } else {
                value(item, indent);
            }
        }
    }

    private void scalar(String text, int parent) {
        if (text.indexOf('\n') >= 0 && literalAllowed(text)) {
            literal(text, parent + STEP);
            return;
        }
        out.append(' ').append(inline(text)).append('\n');
    }

    /** Блок {@code |}: строки как есть, отступ {@code indent}; хвостовые переводы — индикатором. */
    private void literal(String text, int indent) {
        String body = text;
        String chomping;
        if (!text.endsWith("\n")) {
            chomping = "-";
        } else if (text.endsWith("\n\n")) {
            chomping = "+";
            body = text.substring(0, text.length() - 1);
        } else {
            chomping = "";
            body = text.substring(0, text.length() - 1);
        }
        boolean needsIndicator = body.startsWith(" ") || body.startsWith("\n");
        out.append(" |").append(needsIndicator ? String.valueOf(STEP) : "").append(chomping).append('\n');
        for (String line : body.split("\n", -1)) {
            if (!line.isEmpty()) {
                pad(indent).append(line);
            }
            out.append('\n');
        }
    }

    private static String flow(Flow flow) {
        StringBuilder text = new StringBuilder("{");
        flow.entries().forEach((k, v) -> {
            if (text.length() > 1) {
                text.append(", ");
            }
            text.append(k).append(": ").append(v);
        });
        return text.append('}').toString();
    }

    /** Однострочная запись: простой скаляр, если он читается однозначно, иначе в кавычках. */
    static String inline(String text) {
        return plainAllowed(text) ? text : quoted(text);
    }

    static boolean plainAllowed(String text) {
        if (text.isEmpty() || text.startsWith("---") || text.startsWith("...")) {
            return false;
        }
        char first = text.charAt(0);
        if (PLAIN_FIRST_FORBIDDEN.indexOf(first) >= 0) {
            boolean indicatorBeforeText = (first == '-' || first == '?' || first == ':')
                    && text.length() > 1 && text.charAt(1) != ' ';
            if (!indicatorBeforeText) {
                return false;
            }
        }
        if (Character.isWhitespace(first) || Character.isWhitespace(text.charAt(text.length() - 1))
                || text.endsWith(":") || text.contains(": ") || text.contains(" #")) {
            return false;
        }
        return text.codePoints().allMatch(c -> c != '\t' && printable(c));
    }

    /** Блок годится, если в строке нет символов, которые YAML переводит или запрещает. */
    private static boolean literalAllowed(String text) {
        if (text.isBlank()) {
            return false;
        }
        for (String line : text.split("\n", -1)) {
            if (!line.isEmpty() && line.isBlank()) {
                return false;
            }
        }
        return text.codePoints().allMatch(c -> c == '\n' || c == '\t' || printable(c));
    }

    /** Печатные символы YAML без тех, что SnakeYAML считает переводом строки или меткой порядка байтов. */
    private static boolean printable(int c) {
        return (c >= 0x20 && c <= 0x7E) || (c >= 0xA0 && c <= 0xD7FF && c != 0x2028 && c != 0x2029)
                || (c >= 0xE000 && c <= 0xFFFD && c != 0xFEFF) || (c >= 0x10000 && c <= 0x10FFFF);
    }

    static String quoted(String text) {
        StringBuilder quoted = new StringBuilder(text.length() + 2).append('"');
        text.codePoints().forEach(c -> {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\t' -> quoted.append("\\t");
                case '\r' -> quoted.append("\\r");
                default -> {
                    if (printable(c)) {
                        quoted.appendCodePoint(c);
                    } else if (c <= 0xFF) {
                        quoted.append(String.format("\\x%02X", c));
                    } else {
                        quoted.append(String.format("\\u%04X", c));
                    }
                }
            }
        });
        return quoted.append('"').toString();
    }

    private StringBuilder pad(int indent) {
        return out.append(" ".repeat(indent));
    }
}
