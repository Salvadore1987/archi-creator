package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Правила тестовых исходников всех модулей: отчёт читается по {@code @DisplayName}, имя метода — фраза
 * в camelCase, время в тестах зафиксировано, а не взято с системных часов.
 *
 * <p>Проверка по исходникам, а не по байткоду: тестовые классы других модулей в classpath этого модуля
 * не приходят, а их скомпилированные копии в {@code target} могут отстать от исходника. Комментарии
 * и содержимое строк перед разбором вырезаются: упоминание в тексте — не вызов.
 */
class TestSourceRulesTest {

    private static final Path REPOSITORY = Path.of("..");
    /** Сборка, зависимости и Node — исходников проекта там нет, а файлов десятки тысяч. */
    private static final Set<String> SKIPPED = Set.of("target", "node_modules", ".node", ".git", ".mvn");
    /** Аннотации, которыми JUnit помечает тестовый метод. */
    private static final Set<String> TEST_ANNOTATIONS = Set.of(
            "Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate");
    /** Подряд идущие аннотации и объявление метода за ними; скобки аргументов — до двух уровней. */
    private static final Pattern ANNOTATED_METHOD = Pattern.compile(
            "((?:@[\\w.]+(?:\\s*\\((?:[^()]|\\((?:[^()]|\\([^()]*\\))*\\))*\\))?\\s*)+)"
                    + "(?:(?:public|protected|private|static|final|synchronized)\\s+)*"
                    + "(?:<[^>]+>\\s*)?[\\w.<>\\[\\]?, ]+?\\s+(\\w+)\\s*\\(");
    private static final Pattern ANNOTATION_NAME = Pattern.compile("@(?:[\\w]+\\.)*(\\w+)");
    /** Системные часы: время в тестах — константа или {@code Clock.fixed}. */
    private static final Pattern SYSTEM_TIME = Pattern.compile(
            "\\b(?:Instant|LocalDate|LocalDateTime|LocalTime|OffsetDateTime|ZonedDateTime|Year|YearMonth)"
                    + "\\s*\\.\\s*now\\s*\\(|\\bClock\\s*\\.\\s*system(?:UTC|DefaultZone|)\\s*\\("
                    + "|\\bSystem\\s*\\.\\s*currentTimeMillis\\s*\\(");

    /** Нарушения текущего прохода: обход отдаёт их через лямбды, которые ничего не возвращают. */
    private final List<String> found = new ArrayList<>();

    @Test
    @DisplayName("у каждого тестового метода есть @DisplayName — отчёт читается по нему")
    void everyTestHasDisplayName() {
        List<String> violations = scanTests((file, code) -> eachTest(code, (annotations, method) -> {
            if (!annotationNames(annotations).contains("DisplayName")) {
                report(file, code, method, "без @DisplayName");
            }
        }));
        assertThat(violations).as("тестовые методы без @DisplayName").isEmpty();
    }

    @Test
    @DisplayName("имя тестового метода — фраза в camelCase, без подчёркиваний")
    void testNamesAreCamelCasePhrases() {
        List<String> violations = scanTests((file, code) -> eachTest(code, (annotations, method) -> {
            if (method.group(2).contains("_")) {
                report(file, code, method, method.group(2));
            }
        }));
        assertThat(violations).as("имена тестовых методов с подчёркиванием").isEmpty();
    }

    @Test
    @DisplayName("тесты не берут время с системных часов")
    void testsDoNotReadSystemClock() {
        List<String> violations = scanTests((file, code) -> {
            Matcher found = SYSTEM_TIME.matcher(code);
            while (found.find()) {
                record(file, code, found.start(), found.group());
            }
        });
        assertThat(violations).as("системное время в тестах").isEmpty();
    }

    private List<String> scanTests(BiConsumer<Path, String> check) {
        found.clear();
        try {
            Files.walkFileTree(REPOSITORY, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                    return SKIPPED.contains(String.valueOf(dir.getFileName())) ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String path = file.toString();
                    if (path.endsWith(".java") && path.contains("/src/test/java/")) {
                        check.accept(file, code(file));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(found);
    }

    private static void eachTest(String code, BiConsumer<String, Matcher> check) {
        Matcher method = ANNOTATED_METHOD.matcher(code);
        while (method.find()) {
            List<String> names = annotationNames(method.group(1));
            if (names.stream().anyMatch(TEST_ANNOTATIONS::contains)) {
                check.accept(method.group(1), method);
            }
        }
    }

    private static List<String> annotationNames(String annotations) {
        List<String> names = new ArrayList<>();
        Matcher name = ANNOTATION_NAME.matcher(annotations);
        while (name.find()) {
            names.add(name.group(1));
        }
        return names;
    }

    private void report(Path file, String code, Matcher method, String what) {
        record(file, code, method.start(2), what);
    }

    private void record(Path file, String code, int offset, String what) {
        int line = 1 + (int) code.substring(0, offset).chars().filter(c -> c == '\n').count();
        found.add(REPOSITORY.relativize(file) + ":" + line + ": " + what);
    }

    /** Исходник без комментариев и без содержимого строк и символов; переводы строк сохранены. */
    private static String code(Path file) {
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            if (source.startsWith("//", i)) {
                i = skipTo(source, i, "\n", out, false);
            } else if (source.startsWith("/*", i)) {
                i = skipTo(source, i + 2, "*/", out, true);
            } else if (source.startsWith("\"\"\"", i)) {
                out.append("\"\"");
                i = skipTo(source, i + 3, "\"\"\"", out, true);
            } else if (source.charAt(i) == '"' || source.charAt(i) == '\'') {
                char quote = source.charAt(i);
                out.append(quote).append(quote);
                i++;
                while (i < source.length() && source.charAt(i) != quote) {
                    i += source.charAt(i) == '\\' ? 2 : 1;
                }
                i++;
            } else {
                out.append(source.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    /** Пропускает текст до конца {@code end}; переводы строк пропущенного текста остаются для номеров строк. */
    private static int skipTo(String source, int from, String end, StringBuilder out, boolean consumeEnd) {
        int stop = source.indexOf(end, from);
        int last = stop < 0 ? source.length() : stop;
        source.substring(from, last).chars().filter(c -> c == '\n').forEach(c -> out.append('\n'));
        return stop < 0 ? source.length() : consumeEnd ? stop + end.length() : stop;
    }
}
