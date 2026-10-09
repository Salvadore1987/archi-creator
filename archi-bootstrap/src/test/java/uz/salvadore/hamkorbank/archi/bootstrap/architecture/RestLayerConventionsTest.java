package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Устройство REST-слоя: контроллер принимает запрос и вызывает сценарий,
 * а формы запросов и ответов живут отдельно, в пакете {@code dto}.
 *
 * <p>DTO внутри контроллера — это контракт API, спрятанный в обработчике:
 * его не найти по пакету, не переиспользовать во втором контроллере
 * и не увидеть в диффе как изменение контракта. Правило держит сборка,
 * а не ревью: ревью такой код уже пропускало.
 */
class RestLayerConventionsTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";

    private static final DescribedPredicate<JavaClass> WEB_ENDPOINT = DescribedPredicate.describe(
            "контроллер или обработчик ошибок Spring MVC",
            c -> List.of(RestController.class, Controller.class, RestControllerAdvice.class, ControllerAdvice.class)
                    .stream().anyMatch(c::isAnnotatedWith));

    /** Обработчик запроса — без обработчиков ошибок: те переводят отказ в ответ и только. */
    private static final DescribedPredicate<JavaClass> CONTROLLER = DescribedPredicate.describe(
            "контроллер Spring MVC",
            c -> c.isAnnotatedWith(RestController.class) || c.isAnnotatedWith(Controller.class));

    /** Отказ домена или сценария — исключение из пакетов контекстов. */
    private static final DescribedPredicate<JavaClass> CONTEXT_FAILURE = DescribedPredicate.describe(
            "исключение домена или сценария",
            c -> c.isAssignableTo(RuntimeException.class)
                    && (c.getPackageName().startsWith(ROOT + ".modeling")
                    || c.getPackageName().startsWith(ROOT + ".interchange")
                    || c.getPackageName().startsWith(ROOT + ".advisor")));

    /** Порт сценария, кроме каталога текстов: перевод сообщения — работа адаптера. */
    private static final DescribedPredicate<JavaClass> PORT_BUT_TEXTS = DescribedPredicate.describe(
            "порт application-слоя, кроме TextCatalog",
            c -> c.getPackageName().contains(".application.port") && !c.getSimpleName().equals("TextCatalog"));

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("в контроллере и обработчике ошибок нет вложенных типов: DTO — в пакете dto")
    void noNestedTypesInWebEndpoints() {
        ArchRule rule = noClasses().should(beDeclaredInside(WEB_ENDPOINT))
                .because("формы запросов и ответов — контракт API, их место в пакете dto, "
                        + "а не внутри обработчика");

        rule.check(classes);
    }

    @Test
    @DisplayName("record'ы REST-слоя лежат в пакете dto")
    void restRecordsLiveInDtoPackage() {
        ArchRule rule = classes().that().resideInAPackage("..adapter.rest..").and().areRecords()
                .should().resideInAPackage("..adapter.rest.dto..")
                .because("record в REST-адаптере — это DTO, и искать его должно быть можно по пакету");

        rule.check(classes);
    }

    @Test
    @DisplayName("контроллеры и обработчики ошибок не лежат в пакете dto")
    void endpointsAreOutsideDtoPackage() {
        ArchRule rule = noClasses().that(WEB_ENDPOINT).should().resideInAPackage("..dto..")
                .because("пакет dto содержит только формы данных, без поведения");

        rule.check(classes);
    }

    @Test
    @DisplayName("контроллер не отказывает с бизнес-кодом сам: отказы правил — в сценарии и домене")
    void controllersDoNotRaiseContextFailures() {
        ArchRule rule = noClasses().that(CONTROLLER)
                .should().callConstructorWhere(target(owner(CONTEXT_FAILURE)))
                .because("какой формат поддержан, что недоступно, какое правило нарушено — решения сценария; "
                        + "контроллер разбирает запрос и вызывает один сценарий. Для неразборчивого ввода "
                        + "есть ModelingException.invalid(...)");

        rule.check(classes);
    }

    @Test
    @DisplayName("контроллер не ищет объекты сам: «не найдено» отвечает сценарий")
    void controllersDoNotResolveObjects() {
        ArchRule rule = noClasses().that(CONTROLLER)
                .should().callMethodWhere(target(owner(type(ModelingException.class)))
                        .and(target(name("notFound"))))
                .because("выбор объекта по умолчанию и отказ «не найдено» — решение сценария, "
                        + "а не HTTP-адаптера");

        rule.check(classes);
    }

    @Test
    @DisplayName("контроллер вызывает сценарии, а не порты и хранение")
    void controllersCallUseCasesOnly() {
        ArchRule rule = noClasses().that(CONTROLLER)
                .should().dependOnClassesThat(PORT_BUT_TEXTS)
                .orShould().dependOnClassesThat().resideInAPackage("..adapter.persistence..")
                .because("порядок проверок, транзакция и роль живут в сценарии; обход его из контроллера "
                        + "их теряет");

        rule.check(classes);
    }

    private static ArchCondition<JavaClass> beDeclaredInside(DescribedPredicate<JavaClass> outer) {
        return new ArchCondition<>("быть объявленным внутри: " + outer.getDescription()) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                // Анонимные классы и служебные классы компилятора (таблица switch по enum —
                // Outer$1) не формы данных, а детали реализации метода.
                if (item.isAnonymousClass() || item.getName().matches(".*\\$\\d+$")) {
                    return;
                }
                item.getEnclosingClass().filter(outer).ifPresent(enclosing -> events.add(
                        SimpleConditionEvent.satisfied(item, item.getName() + " объявлен внутри "
                                + enclosing.getName())));
            }
        };
    }
}
