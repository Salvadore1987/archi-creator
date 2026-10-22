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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Каждый пакет основного кода всех модулей размечен {@code @NullMarked} в {@code package-info.java}:
 * всё не-null по умолчанию, отсутствие значения помечается {@code @Nullable}.
 *
 * <p>NullAway при компиляции проверяет весь код проекта и без разметки, но разметка — то,
 * что видят IDE, Spring и читатели сигнатур; пакет без неё выглядел бы неразмеченным,
 * а частичная разметка даёт ложную уверенность. Проверка — по исходникам, а не байткоду:
 * {@code package-info} без аннотации в байткод не попадает вовсе.
 */
class NullMarkedPackagesTest {

    private static final Path REPOSITORY = Path.of("..");
    private static final Set<String> SKIPPED = Set.of("target", "node_modules", ".node", ".git", ".mvn", ".claude");
    private static final Pattern MARKED = Pattern.compile("(?m)^@NullMarked\\s*$");
    private static final Pattern IMPORTED = Pattern.compile("(?m)^import org\\.jspecify\\.annotations\\.NullMarked;$");

    /** Модули, ещё не размеченные: список сокращается по контекстам и исчезает. */
    private static final List<String> NOT_YET_MARKED = List.of(
            "archi-interchange/", "archi-advisor/", "archi-bootstrap/");

    @Test
    @DisplayName("каждый пакет основного кода размечен @NullMarked")
    void everyMainPackageIsNullMarked() throws IOException {
        Set<String> violations = new TreeSet<>();
        Files.walkFileTree(REPOSITORY, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                return SKIPPED.contains(String.valueOf(dir.getFileName())) ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                String path = REPOSITORY.relativize(file).toString();
                if (path.endsWith(".java") && path.contains("/src/main/java/")
                        && NOT_YET_MARKED.stream().noneMatch(path::startsWith)
                        && !marked(file.resolveSibling("package-info.java"))) {
                    violations.add(REPOSITORY.relativize(file.getParent()).toString());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        assertThat(violations).as("пакеты без package-info.java с @NullMarked").isEmpty();
    }

    private static boolean marked(Path packageInfo) {
        if (!Files.exists(packageInfo)) {
            return false;
        }
        try {
            String source = Files.readString(packageInfo, StandardCharsets.UTF_8);
            return MARKED.matcher(source).find() && IMPORTED.matcher(source).find();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
