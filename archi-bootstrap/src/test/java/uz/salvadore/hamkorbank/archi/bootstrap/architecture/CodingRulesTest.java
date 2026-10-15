package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Правила кода, которые видны по байткоду основного кода всех модулей: сигнатуры
 * публичных методов, способ внедрения зависимостей, загрузка связей JPA, библиотеки.
 */
class CodingRulesTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";

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
