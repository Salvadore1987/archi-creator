package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;

/**
 * Помощник не изменяет модель — и держится это не только формой типов результата,
 * но и границей модулей: у помощника нет пути к записи в modeling.
 *
 * <p>Модули advisor не видят modeling вовсе. Мост в {@code bootstrap.advisor} видит из modeling
 * только домен, запросы и результаты сценариев и два входных порта, а из них зовёт лишь методы
 * чтения: в {@code WorkspaceApplicationService} есть и запись. Ни сервиса записи, ни порта
 * и адаптера хранения.
 */
class AdvisorArchitectureTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";

    private static final String BRIDGE = ROOT + ".bootstrap.advisor..";

    /** Входные порты modeling, которые мосту помощника можно видеть. */
    private static final Set<String> READ_PORTS = Set.of(ModelQueryApplicationService.class.getName(),
            WorkspaceApplicationService.class.getName());
    /** Методы этих портов, которые мосту можно звать, — только чтение. */
    private static final Set<String> READ_METHODS = Set.of(
            ModelQueryApplicationService.class.getName() + ".getModel",
            ModelQueryApplicationService.class.getName() + ".listModels",
            ModelQueryApplicationService.class.getName() + ".validateModel",
            WorkspaceApplicationService.class.getName() + ".resolveWorkspace",
            WorkspaceApplicationService.class.getName() + ".listWorkspaces");
    /** Домен, запросы и результаты сценариев — данные, а не путь к записи; команды сюда не входят. */
    private static final Set<String> READ_ONLY_PACKAGES = Set.of(ROOT + ".modeling.domain",
            ROOT + ".modeling.application.dto.query", ROOT + ".modeling.application.dto.result");

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
                "класс modeling вне домена, запросов, результатов и портов чтения",
                c -> c.getPackageName().startsWith(ROOT + ".modeling.")
                        && !READ_PORTS.contains(c.getName())
                        && READ_ONLY_PACKAGES.stream().noneMatch(p -> c.getPackageName().equals(p)
                                || c.getPackageName().startsWith(p + ".")));
        noClasses().that().resideInAPackage(BRIDGE)
                .should().dependOnClassesThat(modelingWrite)
                .because("помощник не изменяет модель: мост берёт из modeling только сценарий запросов")
                .check(classes);
    }

    @Test
    @DisplayName("INV-ADV-004: мост помощника зовёт у портов modeling только методы чтения")
    void advisorCallsOnlyReadMethodsOfModeling() {
        DescribedPredicate<JavaMethodCall> writeCall = DescribedPredicate.describe(
                "вызов метода входного порта modeling, кроме чтения",
                call -> call.getTargetOwner().getPackageName().startsWith(ROOT + ".modeling.application.port")
                        && !READ_METHODS.contains(call.getTargetOwner().getName() + "." + call.getName()));
        noClasses().that().resideInAPackage(BRIDGE)
                .should().callMethodWhere(writeCall)
                .because("в порте пространств есть запись: видеть порт не значит звать всё")
                .check(classes);
    }
}
