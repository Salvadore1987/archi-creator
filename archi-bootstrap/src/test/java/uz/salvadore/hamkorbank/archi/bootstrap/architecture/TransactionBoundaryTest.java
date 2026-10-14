package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Границу транзакции держит порт {@code UnitOfWork}, а не аннотации: application-слой
 * без Spring, и вызов {@code write(...)} виден в коде сценария, а не в прокси.
 *
 * <p>Сборка уже запрещает Spring в domain и application; здесь — остальные модули.
 * Реализация порта и публикация событий после коммита живут в {@code bootstrap.wiring},
 * и только там код касается транзакционного API Spring и Jakarta.
 */
class TransactionBoundaryTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";
    private static final String WIRING = ROOT + ".bootstrap.wiring..";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("ADR-0024: ни класс, ни метод не помечены @Transactional")
    void noTransactionalAnnotations() {
        noClasses().should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .orShould().beAnnotatedWith(jakarta.transaction.Transactional.class)
                .because("граница транзакции — вызов UnitOfWork в сценарии, а не прокси вокруг бина")
                .check(classes);
        noMethods().should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .orShould().beAnnotatedWith(jakarta.transaction.Transactional.class)
                .because("граница транзакции — вызов UnitOfWork в сценарии, а не прокси вокруг бина")
                .check(classes);
    }

    @Test
    @DisplayName("ADR-0024: транзакционный API Spring и Jakarta — только в bootstrap.wiring")
    void transactionApiOnlyInWiring() {
        noClasses().that().resideOutsideOfPackage(WIRING)
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.transaction..",
                        "jakarta.transaction..")
                .because("транзакцию открывает и закрывает реализация UnitOfWork, остальной код о ней не знает")
                .check(classes);
    }
}
