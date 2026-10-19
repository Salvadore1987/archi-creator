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
 * Связь ли узел документа, представление ли — решает один метод, {@code DocumentNode.kind()}.
 * Проверка окончания {@code xsi:type} в другом месте основного кода — второе правило того же,
 * которое однажды разойдётся с первым. ArchUnit строковых аргументов не видит, поэтому
 * проверяются исходники.
 */
class DocumentKindRuleTest {

    private static final Path REPOSITORY = Path.of("..");
    /** Сборка, зависимости и Node — исходников проекта там нет, а файлов десятки тысяч. */
    private static final Set<String> SKIPPED = Set.of("target", "node_modules", ".node", ".git", ".mvn");
    /** Единственный файл, где вид узла выводится из окончания типа. */
    private static final String OWNER = "/interchange/domain/document/DocumentNode.java";
    private static final Pattern SUFFIX_CHECK = Pattern.compile("\\.endsWith\\(\\s*\"\\w*(?:Relationship|Model)\"");

    @Test
    @DisplayName("вид узла по окончанию xsi:type выводит только DocumentNode")
    void onlyDocumentNodeClassifiesByTypeSuffix() throws IOException {
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
                if (path.endsWith(".java") && path.contains("/src/main/java/") && !path.endsWith(OWNER)) {
                    scan(file, violations);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        assertThat(violations).as("вид узла документа в обход DocumentNode.kind()").isEmpty();
    }

    private static void scan(Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                Matcher found = SUFFIX_CHECK.matcher(lines.get(i));
                if (found.find()) {
                    violations.add(REPOSITORY.relativize(file) + ":" + (i + 1) + ": " + found.group());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
