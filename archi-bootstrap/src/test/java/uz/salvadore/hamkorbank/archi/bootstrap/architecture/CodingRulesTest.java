package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Правила кода, которые видны по байткоду основного кода всех модулей: сигнатуры
 * публичных методов, способ внедрения зависимостей, загрузка связей JPA, библиотеки.
 */
class CodingRulesTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";
    private static final String JPA = "jakarta.persistence.";
    /** Контексты, исключения домена которых уже сведены под общий корень. */
    private static final List<String> DOMAIN_EXCEPTION_ROOTS = List.of("modeling", "interchange");
    private static final String TRACKED_MAP = ROOT + ".modeling.domain.common.TrackedMap";
    /** Агрегаты, которые держат свои сущности в {@code TrackedMap}, и сам класс. */
    private static final Set<String> TRACKED_OWNERS = Set.of(TRACKED_MAP,
            ROOT + ".modeling.domain.model.ArchitectureModel", ROOT + ".modeling.domain.view.View");
    private static final Set<String> TRACKED_MUTATORS = Set.of("put", "remove", "load", "markPersisted");
    /** Связи, у которых JPA по умолчанию загружает сразу: им нужен явный LAZY. */
    private static final Set<String> EAGER_BY_DEFAULT = Set.of(JPA + "ManyToOne", JPA + "OneToOne");
    private static final Set<String> FETCHING = Set.of(JPA + "ManyToOne", JPA + "OneToOne", JPA + "OneToMany",
            JPA + "ManyToMany", JPA + "ElementCollection", JPA + "Basic");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("Параметры публичных и protected методов — без generic wildcard")
    void noWildcardsInParameters() {
        methods().that().arePublic().or().areProtected()
                .should(haveNoWildcardParameters())
                .because("тип параметра — контракт метода: конкретный тип или параметр типа, а не `?`")
                .check(classes);
    }

    @Test
    @DisplayName("Зависимости — только через конструктор: поле не внедряется и в бине неизменяемо")
    void constructorInjectionOnly() {
        noFields().should().beAnnotatedWith(jakarta.persistence.PersistenceContext.class)
                .orShould().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
                .orShould().beAnnotatedWith(org.springframework.beans.factory.annotation.Value.class)
                .because("зависимость видна в конструкторе, и бин без контейнера собирается вызовом new")
                .check(classes);
        fields().that().areNotStatic()
                .and().areDeclaredInClassesThat().areMetaAnnotatedWith(org.springframework.stereotype.Component.class)
                .should().beFinal()
                .because("поле бина задаётся один раз в конструкторе")
                .check(classes);
    }

    @Test
    @DisplayName("Связи и коллекции JPA загружаются лениво")
    void noEagerFetch() {
        fields().should(fetchLazily())
                .because("EAGER тянет граф при каждом чтении сущности; нужное читается JOIN FETCH в запросе")
                .check(classes);
    }

    private static ArchCondition<JavaField> fetchLazily() {
        return new ArchCondition<>("fetch lazily") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                for (JavaAnnotation<JavaField> annotation : field.getAnnotations()) {
                    String type = annotation.getRawType().getName();
                    if (!FETCHING.contains(type)) {
                        continue;
                    }
                    Optional<String> fetch = annotation.get("fetch")
                            .map(value -> ((JavaEnumConstant) value).name());
                    boolean eager = fetch.map("EAGER"::equals).orElse(EAGER_BY_DEFAULT.contains(type));
                    if (eager) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getFullName() + " is fetched eagerly by @"
                                        + annotation.getRawType().getSimpleName()));
                    }
                }
            }
        };
    }

    @Test
    @DisplayName("Сущности агрегата modeling меняет только сам агрегат")
    void trackedEntitiesChangeOnlyInsideAggregate() {
        codeUnits().should(notChangeTrackedEntitiesOutsideAggregate())
                .because("вставка и удаление мимо агрегата обходят его проверки")
                .check(classes);
    }

    private static ArchCondition<JavaCodeUnit> notChangeTrackedEntitiesOutsideAggregate() {
        return new ArchCondition<>("not change tracked entities outside the aggregate") {
            @Override
            public void check(JavaCodeUnit unit, ConditionEvents events) {
                if (TRACKED_OWNERS.contains(unit.getOwner().getName())) {
                    return;
                }
                for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
                    if (call.getTargetOwner().getName().equals(TRACKED_MAP)
                            && TRACKED_MUTATORS.contains(call.getName())) {
                        events.add(SimpleConditionEvent.violated(unit, unit.getFullName() + " calls "
                                + call.getTarget().getFullName()));
                    }
                }
            }
        };
    }

    @Test
    @DisplayName("Исключения домена наследуют DomainException своего контекста")
    void domainExceptionsShareContextRoot() {
        for (String context : DOMAIN_EXCEPTION_ROOTS) {
            String root = ROOT + "." + context + ".domain.common.DomainException";
            classes().that().resideInAPackage(ROOT + "." + context + ".domain..")
                    .and().areAssignableTo(Throwable.class)
                    .and().doNotHaveFullyQualifiedName(root)
                    .should().beAssignableTo(root)
                    .because("отказ домена — одна иерархия: код, вид отказа и сообщение с ключом у всех")
                    .check(classes);
        }
    }

    @Test
    @DisplayName("JSON — Jackson 3: ни Jackson 2, ни его исключений в коде")
    void jacksonThreeOnly() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson.databind..",
                        "com.fasterxml.jackson.core..")
                .because("Jackson 2 устарел в Spring Boot 4; вторая версия рядом — два набора настроек и ошибок")
                .check(classes);
    }

    /**
     * Выборка «всё» без предела — только там, где список отдаётся целиком по контракту API
     * и ждёт пагинации; искать одну запись среди всех нельзя.
     */
    private static final Set<String> UNBOUNDED_LIST_CALLERS = Set.of(
            ROOT + ".modeling.application.service.WorkspaceService.list()");

    @Test
    @DisplayName("findAll() порта без предела — только в списках, которые ждут пагинации")
    void noUnboundedFindAll() {
        codeUnits().should(notCallUnboundedFindAll())
                .because("выборка без предела растёт вместе с базой; одна запись читается запросом с LIMIT")
                .check(classes);
    }

    private static ArchCondition<JavaCodeUnit> notCallUnboundedFindAll() {
        return new ArchCondition<>("not call findAll() of a port without a bound") {
            @Override
            public void check(JavaCodeUnit unit, ConditionEvents events) {
                if (UNBOUNDED_LIST_CALLERS.contains(unit.getFullName())) {
                    return;
                }
                for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
                    if (call.getName().equals("findAll") && call.getTarget().getRawParameterTypes().isEmpty()
                            && call.getTargetOwner().getPackageName().contains(".application.port")) {
                        events.add(SimpleConditionEvent.violated(unit, unit.getFullName() + " calls "
                                + call.getTarget().getFullName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> haveNoWildcardParameters() {
        return new ArchCondition<>("have no wildcard in parameter types") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaType type : method.getParameterTypes()) {
                    if (hasWildcard(type)) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " has wildcard parameter " + type.getName()));
                    }
                }
            }
        };
    }

    private static boolean hasWildcard(JavaType type) {
        if (type instanceof JavaWildcardType) {
            return true;
        }
        if (type instanceof JavaParameterizedType parameterized) {
            return parameterized.getActualTypeArguments().stream().anyMatch(CodingRulesTest::hasWildcard);
        }
        return false;
    }
}
