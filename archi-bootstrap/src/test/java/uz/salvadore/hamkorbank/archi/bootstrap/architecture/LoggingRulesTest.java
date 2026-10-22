package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * Журнал основного кода: один API — SLF4J, два уровня — {@code INFO} для хода работы и {@code ERROR}
 * для того, что требует внимания, сообщение — шаблон с плейсхолдерами {@code {}}.
 *
 * <p>Уровни и API видны по байткоду. Склейку сообщения байткод не отличает от любой другой строки,
 * поэтому её ловит проверка исходников основного кода всех модулей.
 */
class LoggingRulesTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";
    private static final Path REPOSITORY = Path.of("..");
    /** Сборка, зависимости, Node и рабочие копии агентов — исходников проекта там нет или они чужие. */
    private static final Set<String> SKIPPED = Set.of("target", "node_modules", ".node", ".git", ".mvn", ".claude");
    /** Вызов журнала до конца выражения: поле журнала в проекте называется {@code LOG}. */
    private static final Pattern LOG_CALL =
            Pattern.compile("\\bLOG\\s*\\.\\s*\\w+\\s*\\((?:[^;\"]|\"(?:[^\"\\\\]|\\\\.)*\")*;");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern CONCATENATION = Pattern.compile("\"\"\\s*\\+|\\+\\s*\"\"");

    private static JavaClasses main;

    @BeforeAll
    static void importMain() {
        main = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("журнал основного кода — SLF4J, а не System.Logger и не java.util.logging")
    void onlySlf4j() {
        noClasses()
                .should().dependOnClassesThat().belongToAnyOf(System.Logger.class, System.LoggerFinder.class)
                .orShould().dependOnClassesThat().resideInAPackage("java.util.logging..")
                .because("бэкенд журнала один — Logback через SLF4J; System.Logger уходит в него мостом JUL")
                .check(main);
    }

    @Test
    @DisplayName("в основном коде журнал пишет только INFO и ERROR")
    void onlyInfoAndError() {
        noClasses()
                .should().callMethodWhere(target(owner(type(Logger.class))).and(target(nameMatching(
                        "(debug|trace|warn|is(Debug|Trace|Warn)Enabled|at(Debug|Trace|Warn))"))))
                .because("DEBUG и TRACE в prod не видны, а WARN допустим только в уровнях чужих библиотек")
                .check(main);
    }

    @Test
    @DisplayName("сообщение журнала — шаблон с {}, а не склейка строк")
    void noConcatenationInLogCalls() throws IOException {
        List<String> violations = new ArrayList<>();
        Files.walkFileTree(REPOSITORY, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                return SKIPPED.contains(String.valueOf(dir.getFileName())) ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (file.toString().endsWith(".java") && file.toString().contains("/src/main/java/")) {
                    scan(file, violations);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        assertThat(violations).as("склейка строк в вызове журнала").isEmpty();
    }

    private static void scan(Path file, List<String> violations) throws IOException {
        Matcher call = LOG_CALL.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (call.find()) {
            String blanked = STRING_LITERAL.matcher(call.group()).replaceAll("\"\"");
            if (CONCATENATION.matcher(blanked).find()) {
                violations.add(REPOSITORY.relativize(file) + ": " + call.group().replaceAll("\\s+", " "));
            }
        }
    }
}
