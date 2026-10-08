package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Изоляция bounded context'ов друг от друга.
 *
 * <p>Раскладку по модулям держит сборка (`ADR-0001`), но только внутри одного
 * контекста: `archi-modeling-domain` не видит Spring, потому что зависимости
 * нет. Между контекстами так не выходит — все двенадцать модулей встречаются
 * в `archi-bootstrap`, и оттуда любой класс технически виден любому. Поэтому
 * карта контекстов держится правилом, а не зависимостью.
 *
 * <p>Что разрешено, задаёт
 * {@code spec/domain/bounded-contexts.yaml}:
 *
 * <ul>
 *   <li>{@code modeling ↔ interchange} — partnership, движение в обе стороны;</li>
 *   <li>{@code advisor → modeling} — published language плюс ACL, только чтение;</li>
 *   <li>{@code modeling → advisor} — запрещено: помощник не изменяет модель (FR-22),
 *       и обратной стрелки в карте нет;</li>
 *   <li>{@code advisor ↔ interchange} — связи нет вовсе, ни в какую сторону.</li>
 * </ul>
 *
 * <p>Правила проверяются в `archi-bootstrap`, потому что только сюда приходят
 * все модули разом.
 */
class BoundedContextIsolationTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";
    private static final String MODELING = ROOT + ".modeling..";
    private static final String INTERCHANGE = ROOT + ".interchange..";
    private static final String ADVISOR = ROOT + ".advisor..";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("modeling не знает про advisor: помощник не изменяет модель (FR-22)")
    void modelingDoesNotDependOnAdvisor() {
        ArchRule rule = noClasses().that().resideInAPackage(MODELING)
                .should().dependOnClassesThat().resideInAPackage(ADVISOR)
                .because("в карте контекстов нет стрелки modeling → advisor: "
                        + "помощник читает модель, а не наоборот (FR-22)");

        rule.check(classes);
    }

    @Test
    @DisplayName("advisor не знает про interchange: связи между ними нет")
    void advisorDoesNotDependOnInterchange() {
        ArchRule rule = noClasses().that().resideInAPackage(ADVISOR)
                .should().dependOnClassesThat().resideInAPackage(INTERCHANGE)
                .because("в карте контекстов связи advisor ↔ interchange нет ни в какую "
                        + "сторону: помощнику нечего делать с сериализацией");

        rule.check(classes);
    }

    @Test
    @DisplayName("interchange не знает про advisor: связи между ними нет")
    void interchangeDoesNotDependOnAdvisor() {
        ArchRule rule = noClasses().that().resideInAPackage(INTERCHANGE)
                .should().dependOnClassesThat().resideInAPackage(ADVISOR)
                .because("в карте контекстов связи interchange ↔ advisor нет ни в какую "
                        + "сторону: кодеку нечего делать с промптами");

        rule.check(classes);
    }
}
