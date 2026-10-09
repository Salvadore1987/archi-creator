package uz.salvadore.hamkorbank.archi.bootstrap.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.MetamodelPhase;

/**
 * Геометрия фигур и спрайт иконок, общие для канвы и серверного писателя SVG,
 * лежат в classpath приложения и покрывают каталог типов сервера.
 */
class SharedShapesTest {

    private static final Pattern SYMBOL = Pattern.compile("<symbol id=\"([^\"]+)\"");

    @Test
    @DisplayName("UI-019: у каждого типа фазы 1 есть запись в shapes.json")
    void everyPhaseOneElementHasShape() throws IOException {
        JsonNode shapes = read("ui/shapes.json").get("shapes");
        List<String> missing = ArchiTypeRegistry.archimate32().concepts(ConceptKind.ELEMENT).stream()
                .filter(c -> c.phase().filter(p -> p == MetamodelPhase.PHASE_1).isPresent())
                .map(c -> c.type().toString())
                .filter(type -> !shapes.has(type))
                .toList();
        assertThat(missing).isEmpty();
        assertThat(shapes.has("archimate:Junction")).isTrue();
    }

    @Test
    @DisplayName("UI-019: каждая иконка из shapes.json есть в общем спрайте")
    void everyIconIsInSprite() throws IOException {
        Set<String> symbols;
        try (InputStream in = resource("ui/icons.svg")) {
            symbols = SYMBOL.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8)).results()
                    .map(m -> m.group(1)).collect(Collectors.toSet());
        }
        JsonNode root = read("ui/shapes.json");
        root.get("shapes").forEach(entry -> {
            JsonNode icon = entry.get("corner_icon");
            if (!icon.isNull()) {
                assertThat(symbols).contains(icon.get("sprite_id").asString());
            }
        });
        assertThat(symbols).contains(root.get("fallback").get("corner_icon").get("sprite_id").asString());
    }

    @Test
    @DisplayName("UI-010: токены стиля в classpath — тот же файл, что читает сборка фронтенда")
    void designTokensOnClasspath() throws IOException {
        try (InputStream in = resource("ui/design-tokens.yaml")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("--layer-application");
        }
    }

    private static JsonNode read(String path) throws IOException {
        try (InputStream in = resource(path)) {
            return JsonMapper.builder().build().readTree(in);
        }
    }

    private static InputStream resource(String path) {
        InputStream in = SharedShapesTest.class.getClassLoader().getResourceAsStream(path);
        assertThat(in).as(path).isNotNull();
        return in;
    }
}
