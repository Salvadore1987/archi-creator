package uz.salvadore.hamkorbank.archi.bootstrap.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Box;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.CornerIcon;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.LayerPaint;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.ShapeEntry;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Size;

/**
 * Визуальный язык канвы из classpath: {@code ui/shapes.json}, {@code ui/design-tokens.yaml},
 * {@code ui/icons.svg} — те же файлы, что собирает фронтенд. Второй копии значений
 * у сервера нет.
 */
public final class SharedNotation {

    static final String SHAPES = "ui/shapes.json";
    static final String TOKENS = "ui/design-tokens.yaml";
    static final String ICONS = "ui/icons.svg";

    /** Группы токенов с плоскими значениями {@code {value: …}}; слои читаются отдельно. */
    private static final String[] VALUE_GROUPS = {"shape", "typography", "edges", "canvas"};
    private static final Pattern SYMBOL = Pattern.compile("<symbol\\s+id=\"([^\"]+)\"[^>]*>(.*?)</symbol>",
            Pattern.DOTALL);
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private SharedNotation() {
    }

    /**
     * Собирает нотацию из общих с фронтендом файлов classpath: размеры и силуэты фигур, токены стиля и иконки; подписи
     * набираются семейством {@code family}.
     */
    public static Notation load(String family) {
        JsonNode shapes = JsonMapper.builder().build().readTree(text(SHAPES));
        Map<String, Size> sizes = new HashMap<>();
        shapes.get("default_size_by_type").properties().forEach(e -> sizes.put(e.getKey(), size(e.getValue())));
        Map<String, ShapeEntry> entries = new LinkedHashMap<>();
        shapes.get("shapes").properties().forEach(e -> entries.put(e.getKey(), entry(e.getValue())));

        Map<String, Object> tokens = tokens(text(TOKENS));
        Map<String, LayerPaint> layers = new LinkedHashMap<>();
        map(group(tokens, "layers")).forEach((key, value) -> layers.put(key,
                new LayerPaint(String.valueOf(map(value).get("fill")), String.valueOf(map(value).get("border")))));
        Map<String, String> values = new LinkedHashMap<>();
        for (String group : VALUE_GROUPS) {
            map(group(tokens, group)).forEach((key, value) -> values.put(key, String.valueOf(map(value).get("value"))));
        }

        Map<String, String> icons = new LinkedHashMap<>();
        Matcher symbols = SYMBOL.matcher(COMMENT.matcher(text(ICONS)).replaceAll(""));
        while (symbols.find()) {
            icons.put(symbols.group(1), symbols.group(2).strip().replaceAll("\\s*\\n\\s*", ""));
        }
        return new Notation(size(shapes.get("default_size")), sizes, entries, entry(shapes.get("fallback")), layers,
                values, icons, family);
    }

    /** Файл токенов открывается заголовком спецификации отдельным документом YAML; значения — в том, где слои. */
    private static Map<String, Object> tokens(String yaml) {
        for (Object document : new Yaml().loadAll(yaml)) {
            if (document instanceof Map<?, ?> candidate && candidate.containsKey("layers")) {
                return map(candidate);
            }
        }
        throw new IllegalStateException(TOKENS);
    }

    private static ShapeEntry entry(JsonNode node) {
        JsonNode icon = node.get("corner_icon");
        JsonNode label = node.get("label_box");
        return new ShapeEntry(node.get("outline").asString(),
                present(icon).map(i -> new CornerIcon(i.get("sprite_id").asString(), i.get("x").asDouble(),
                        i.get("y").asDouble(), i.get("size").asDouble())).orElse(null),
                present(label).map(b -> new Box(b.get("x").asDouble(), b.get("y").asDouble(),
                        b.get("width").asDouble(), b.get("height").asDouble())).orElse(null),
                node.get("radius_token").asString(),
                present(node.get("layer")).map(JsonNode::asString).orElse(null),
                present(node.get("dash")).map(JsonNode::asString).orElse(null));
    }

    private static Optional<JsonNode> present(JsonNode node) {
        return Optional.ofNullable(node).filter(n -> !n.isNull());
    }

    private static Size size(JsonNode node) {
        return new Size(node.get("width").asDouble(), node.get("height").asDouble());
    }

    /** Группа токенов: файл токенов её объявляет, без неё нотацию не собрать. */
    private static Object group(Map<String, Object> tokens, String name) {
        return Objects.requireNonNull(tokens.get(name), name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static String text(String path) {
        try (InputStream in = SharedNotation.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException(path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
