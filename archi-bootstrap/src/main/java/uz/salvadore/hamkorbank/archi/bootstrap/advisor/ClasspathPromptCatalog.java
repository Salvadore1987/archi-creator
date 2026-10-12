package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import uz.salvadore.hamkorbank.archi.advisor.application.port.PromptCatalog;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;

/**
 * Промпты из ресурсов {@code advisor/prompts/}: общие правила, задание функции
 * и описания инструментов. Язык ответа дописывается последней строкой ({@code language.md}) — язык
 * интерфейса автора; сами промпты на английском, одном для всех языков ответа.
 */
public final class ClasspathPromptCatalog implements PromptCatalog {

    private static final String BASE = "advisor/prompts/";

    private final String common = read("common.md");
    private final String language = read("language.md");
    private final Map<AdvisorFunction, String> tasks = new EnumMap<>(AdvisorFunction.class);
    private final Map<String, String> tools = new ConcurrentHashMap<>();

    public ClasspathPromptCatalog() {
        for (AdvisorFunction function : AdvisorFunction.values()) {
            tasks.put(function, read(function.name().toLowerCase(Locale.ROOT) + ".md"));
        }
    }

    @Override
    public String system(AdvisorFunction function, Locale answerLanguage) {
        return common + "\n" + tasks.get(function) + "\n"
                + language.formatted(answerLanguage.getDisplayLanguage(Locale.ENGLISH));
    }

    @Override
    public String toolDescription(String toolName) {
        return tools.computeIfAbsent(toolName, name -> read("tool-" + name + ".md").strip());
    }

    private static String read(String name) {
        try (InputStream in = ClasspathPromptCatalog.class.getClassLoader().getResourceAsStream(BASE + name)) {
            if (in == null) {
                throw new IllegalStateException(BASE + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
