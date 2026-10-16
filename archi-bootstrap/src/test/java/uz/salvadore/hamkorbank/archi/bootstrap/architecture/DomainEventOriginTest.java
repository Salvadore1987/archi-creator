package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSnapshotCommittedEvent;

/**
 * Событие о переходе состояния рождается в бизнес-методе агрегата, вместе с самим
 * переходом: application-сервис забирает готовые события и публикует, но не собирает их.
 * Иначе изменение состояния без события — вопрос внимательности, а не устройства кода.
 *
 * <p>Исключение — событие о факте внешней системы: его не порождает ни один агрегат.
 * Такие события перечислены поимённо с причиной.
 */
class DomainEventOriginTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";

    /** Событие, которое создаёт сервис, — и почему не агрегат. */
    private static final Map<String, String> EXTERNAL_FACTS = Map.of(
            GitSnapshotCommittedEvent.class.getName(),
            "коммит в Git состоялся — факт внешней системы; публикуется и без записи привязки");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"modeling", "interchange", "advisor"})
    @DisplayName("ADR-0023: событие контекста создаётся только в его домене")
    void eventsAreBornInDomain(String context) {
        String marker = ROOT + "." + context + ".domain.common.DomainEvent";
        DescribedPredicate<JavaConstructorCall> eventConstructor = DescribedPredicate.describe(
                "конструктор события " + context + ", кроме фактов внешней системы",
                call -> call.getTargetOwner().isAssignableTo(marker)
                        && !EXTERNAL_FACTS.containsKey(call.getTargetOwner().getName()));
        noClasses().that().resideOutsideOfPackage(ROOT + "." + context + ".domain..")
                .should().callConstructorWhere(eventConstructor)
                .because("событие рождается в бизнес-методе агрегата вместе с переходом состояния")
                .check(classes);
    }
}
