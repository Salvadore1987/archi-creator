package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorReference;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * UUID проекта создаются и разбираются только библиотекой java-uuid-generator:
 * версия 7 — {@code Generators.timeBasedEpochGenerator} (в контекстах — через {@code UuidV7}
 * с часами приложения), строка — {@code UUIDUtil.uuid}. Тип значения остаётся
 * {@code java.util.UUID}, запрещены его фабрики и конструктор.
 *
 * <p>Две проверки, потому что одной не хватает: ArchUnit видит байткод основного кода,
 * включая вызовы по полному имени класса, но тестовые классы других модулей сюда
 * не приходят — их держит проверка исходников всех модулей.
 */
class UuidRulesTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";
    private static final Path REPOSITORY = Path.of("..");
    /** Сборка, зависимости и Node — исходников проекта там нет, а файлов десятки тысяч. */
    private static final Set<String> SKIPPED = Set.of("target", "node_modules", ".node", ".git", ".mvn");
    /**
     * Фабрики и конструктор {@code java.util.UUID} в исходнике, с полным именем класса или без,
     * вызовом или ссылкой на метод ({@code UUID::randomUUID}, {@code UUID::new}).
     */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(?:java\\.util\\.)?UUID\\s*\\.\\s*(?:randomUUID|nameUUIDFromBytes|fromString)\\s*\\("
                    + "|\\bnew\\s+(?:java\\.util\\.)?UUID\\s*\\("
                    + "|\\b(?:java\\.util\\.)?UUID\\s*::\\s*(?:randomUUID|nameUUIDFromBytes|fromString|new)\\b");
    /** Фабрики {@code java.util.UUID}, которые проект не вызывает и не передаёт ссылкой. */
    private static final Set<String> FACTORIES = Set.of("randomUUID", "nameUUIDFromBytes", "fromString");

    @Test
    @DisplayName("основной код не создаёт и не разбирает UUID средствами java.util.UUID")
    void mainCodeUsesLibrary() {
        noClasses()
                .should().callMethod(UUID.class, "randomUUID")
                .orShould().callMethod(UUID.class, "nameUUIDFromBytes", byte[].class)
                .orShould().callMethod(UUID.class, "fromString", String.class)
                .orShould().callConstructor(UUID.class, long.class, long.class)
                .orShould(referenceUuidFactories())
                .because("UUID проекта создаёт и разбирает java-uuid-generator")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(ROOT));
    }

    /** Ссылка на фабрику или конструктор {@code UUID} — тот же вызов, отложенный до {@code get()}. */
    private static ArchCondition<JavaClass> referenceUuidFactories() {
        return new ArchCondition<>("reference factories or constructor of java.util.UUID") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethodReference reference : javaClass.getMethodReferencesFromSelf()) {
                    if (reference.getTargetOwner().isEquivalentTo(UUID.class)
                            && FACTORIES.contains(reference.getName())) {
                        events.add(SimpleConditionEvent.satisfied(javaClass, reference.getDescription()));
                    }
                }
                for (JavaConstructorReference reference : javaClass.getConstructorReferencesFromSelf()) {
                    if (reference.getTargetOwner().isEquivalentTo(UUID.class)) {
                        events.add(SimpleConditionEvent.satisfied(javaClass, reference.getDescription()));
                    }
                }
            }
        };
    }

    @Test
    @DisplayName("исходники всех модулей, тесты включительно, не создают UUID в обход библиотеки")
    void sourcesOfAllModulesUseLibrary() throws IOException {
        Path self = Path.of("src/test/java", UuidRulesTest.class.getName().replace('.', '/') + ".java")
                .toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();
        Files.walkFileTree(REPOSITORY, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                return SKIPPED.contains(String.valueOf(dir.getFileName())) ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                String path = file.toString();
                if (path.endsWith(".java") && (path.contains("/src/main/java/") || path.contains("/src/test/java/"))
                        && !file.toAbsolutePath().normalize().equals(self)) {
                    scan(file, violations);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        assertThat(violations).as("UUID в обход java-uuid-generator").isEmpty();
    }

    private static void scan(Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                Matcher found = FORBIDDEN.matcher(lines.get(i));
                if (found.find()) {
                    violations.add(REPOSITORY.relativize(file) + ":" + (i + 1) + ": " + found.group());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
