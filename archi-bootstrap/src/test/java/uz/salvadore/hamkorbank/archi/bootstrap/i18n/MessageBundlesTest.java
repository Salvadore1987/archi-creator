package uz.salvadore.hamkorbank.archi.bootstrap.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.bootstrap.web.BootstrapMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Ключи сообщений и файлы локалей согласованы: каждый ключ из кода переведён
 * на каждый язык, лишних ключей нет, подстановки в переводах те же. Без этой
 * проверки забытый перевод виден пользователю голым ключом, а потерянная
 * подстановка — фразой без имени объекта.
 */
class MessageBundlesTest {

    private static final List<String> LOCALES = List.of("messages.properties", "messages_en.properties");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    @Test
    @DisplayName("UI-021: каждый ключ из кода есть во всех локалях, лишних ключей нет")
    void everyKeyTranslated() throws Exception {
        Set<String> keys = codeKeys();
        for (String locale : LOCALES) {
            Set<String> bundle = load(locale).stringPropertyNames();
            assertThat(new TreeSet<>(keys)).as("ключи без перевода в " + locale)
                    .isSubsetOf(bundle);
            assertThat(new TreeSet<>(bundle)).as("ключи " + locale + ", которых нет в коде")
                    .isSubsetOf(keys);
        }
    }

    @Test
    @DisplayName("UI-021: подстановки {n} в переводах совпадают с русским текстом")
    void placeholdersMatch() throws Exception {
        Properties ru = load(LOCALES.getFirst());
        List<String> mismatched = new ArrayList<>();
        for (String locale : LOCALES.subList(1, LOCALES.size())) {
            Properties other = load(locale);
            for (String key : ru.stringPropertyNames()) {
                if (!placeholders(ru.getProperty(key)).equals(placeholders(other.getProperty(key, "")))) {
                    mismatched.add(locale + ": " + key);
                }
            }
        }
        assertThat(mismatched).isEmpty();
    }

    @Test
    @DisplayName("UI-021: ключ объявлен одной константой: два имени на один ключ — признак копирования")
    void keysAreUnique() throws Exception {
        List<String> all = Stream.of(ModelingMessages.class, InterchangeMessages.class, BootstrapMessages.class)
                .flatMap(MessageBundlesTest::constants).toList();
        assertThat(all).doesNotHaveDuplicates();
    }

    private static Set<String> codeKeys() {
        Set<String> keys = new HashSet<>();
        Stream.of(ModelingMessages.class, InterchangeMessages.class, BootstrapMessages.class)
                .flatMap(MessageBundlesTest::constants).forEach(keys::add);
        return keys;
    }

    private static Stream<String> constants(Class<?> type) {
        return Stream.of(type.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
                .map(MessageBundlesTest::value);
    }

    private static String value(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessageBundlesTest.class.getClassLoader().getResourceAsStream("i18n/" + name)) {
            assertThat(in).as(name).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
