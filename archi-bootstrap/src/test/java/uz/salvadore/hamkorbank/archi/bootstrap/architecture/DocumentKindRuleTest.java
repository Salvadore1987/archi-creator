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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связь ли узел документа, представление ли — решает один метод, {@code DocumentNode.kind()};
 * группа ли объект представления, заметка ли, линия связи ли — {@code DocumentNode.viewObject()},
 * а в modeling — константы {@code DiagramType}.
 * Проверка {@code xsi:type} в другом месте основного кода — второе правило того же, которое
 * однажды разойдётся с первым. ArchUnit строковых аргументов не видит, поэтому проверяются исходники.
 */
class DocumentKindRuleTest {

    private static final Path REPOSITORY = Path.of("..");
    /** Сборка, зависимости и Node — исходников проекта там нет, а файлов десятки тысяч. */
    private static final Set<String> SKIPPED = Set.of("target", "node_modules", ".node", ".git", ".mvn");
    /** Единственный файл, где вид узла выводится из окончания типа. */
    private static final String OWNER = "/interchange/domain/document/DocumentNode.java";
    /** Типы объектов представления в modeling — константы, а не строки в местах сравнения. */
    private static final String MODELING_OWNER = "/modeling/domain/view/DiagramType.java";
    private static final Pattern SUFFIX_CHECK = Pattern.compile("\\.endsWith\\(\\s*\"\\w*(?:Relationship|Model)\"");
    /** Типы объектов представления Archi. */
    private static final Pattern VIEW_OBJECT_TYPE = Pattern.compile(
            "\"archimate:(?:DiagramObject|Group|Note|DiagramModelReference|Connection|DiagramModelConnection)\"");

    @Test
    @DisplayName("вид узла по окончанию xsi:type выводит только DocumentNode")
    void onlyDocumentNodeClassifiesByTypeSuffix() throws IOException {
        assertThat(violations(SUFFIX_CHECK, Set.of(OWNER)))
                .as("вид узла документа в обход DocumentNode.kind()").isEmpty();
    }

    @Test
    @DisplayName("вид объекта представления по xsi:type выводят только DocumentNode и DiagramType")
    void onlyDocumentNodeClassifiesViewObjects() throws IOException {
        assertThat(violations(VIEW_OBJECT_TYPE, Set.of(OWNER, MODELING_OWNER)))
                .as("вид объекта представления в обход DocumentNode.viewObject() и DiagramType").isEmpty();
    }

    /** Совпадения шаблона в основном коде всех модулей, кроме файлов-владельцев правила. */
    private static List<String> violations(Pattern pattern, Set<String> owners) throws IOException {
        List<String> violations = new ArrayList<>();
        Files.walkFileTree(REPOSITORY, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                return SKIPPED.contains(String.valueOf(dir.getFileName())) ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                String path = file.toString().replace('\\', '/');
                if (path.endsWith(".java") && path.contains("/src/main/java/")
                        && owners.stream().noneMatch(path::endsWith)) {
                    scan(file, pattern, violations);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return violations;
    }

    private static void scan(Path file, Pattern pattern, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                Matcher found = pattern.matcher(lines.get(i));
                if (found.find()) {
                    violations.add(REPOSITORY.relativize(file) + ":" + (i + 1) + ": " + found.group());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
