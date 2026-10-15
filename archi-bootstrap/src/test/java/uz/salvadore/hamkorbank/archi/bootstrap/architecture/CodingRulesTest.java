package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
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
