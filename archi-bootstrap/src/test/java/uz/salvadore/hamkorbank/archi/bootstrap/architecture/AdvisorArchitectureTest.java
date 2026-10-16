package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ResolveWorkspaceQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ResolveWorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewPlacements;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryService;

/**
 * Помощник не изменяет модель — и держится это не только формой типов результата,
 * но и границей модулей: у помощника нет пути к записи в modeling.
 *
 * <p>Модули advisor не видят modeling вовсе. Мост в {@code bootstrap.advisor} видит,
 * но только сценарий запросов, пространства и запись размещений — ни одного сервиса
 * записи и ни одного порта хранения.
 */
class AdvisorArchitectureTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";

    /** Что мосту помощника можно взять из application-слоя modeling. */
    private static final Set<String> READ_ONLY_MODELING = Set.of(ModelQueryService.class.getName(),
            ModelQueryService.OpenedModel.class.getName(), WorkspaceApplicationService.class.getName(),
            ResolveWorkspaceQuery.class.getName(), ResolveWorkspaceResponse.class.getName(),
            ViewPlacements.class.getName());

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("INV-ADV-004: модули помощника не зависят от modeling — ни от записи, ни от чтения")
    void advisorModulesDoNotSeeModeling() {
        noClasses().that().resideInAPackage(ROOT + ".advisor..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".modeling..")
                .because("помощник читает модель через свой порт, реализованный мостом; "
                        + "зависимость от modeling открыла бы ему сервисы записи")
                .check(classes);
    }

    @Test
    @DisplayName("INV-ADV-004: у моста помощника нет порта записи в modeling")
    void advisorHasNoWritePortToModeling() {
        DescribedPredicate<JavaClass> modelingWrite = DescribedPredicate.describe(
                "сервис или порт application-слоя modeling, кроме чтения",
                c -> c.getPackageName().startsWith(ROOT + ".modeling.application")
                        && !READ_ONLY_MODELING.contains(c.getName()));
        noClasses().that().resideInAPackage(ROOT + ".bootstrap.advisor..")
                .should().dependOnClassesThat(modelingWrite)
                .because("помощник не изменяет модель: мост берёт из modeling только сценарий запросов")
                .check(classes);
    }
}
