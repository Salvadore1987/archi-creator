package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.conditions.ArchConditions.be;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Направление зависимостей гексагональной раскладки, которое не держат модули Maven.
 *
 * <p>Модуль домена объявляет одну зависимость, но enforcer запрещает лишь известные фреймворки: любая
 * другая библиотека прошла бы в домен молча. Выходной порт — контракт, а не реализация, хотя модуль
 * application мог бы положить рядом с ним класс с поведением. JPA-сущность компилируется в любом модуле,
 * где есть JPA, — в том числе в {@code archi-bootstrap}.
 */
class HexagonalArchitectureTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";
    /** Что домену можно кроме своего контекста: JDK (включая StAX и DOM) и генератор UUID. */
    private static final List<String> DOMAIN_ALLOWED = List.of("java..", "javax.xml..", "org.w3c.dom..",
            "org.xml.sax..", "com.fasterxml.uuid..");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    static Stream<String> contexts() {
        return Stream.of("modeling", "interchange", "advisor");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contexts")
    @DisplayName("Домен зависит только от JDK, генератора UUID и своего контекста")
    void domainDependsOnJdkAndUuidOnly(String context) {
        String domain = ROOT + "." + context + ".domain..";
        String[] allowed = Stream.concat(DOMAIN_ALLOWED.stream(), Stream.of(domain)).toArray(String[]::new);
        classes().that().resideInAPackage(domain)
                .should().onlyDependOnClassesThat().resideInAnyPackage(allowed)
                .because("домен без фреймворков и библиотек: единственная его зависимость — генератор UUID")
                .check(classes);
    }

    @Test
    @DisplayName("В пакетах портов — интерфейсы и их сообщения, без реализаций")
    void portsAreContracts() {
        classes().that().resideInAPackage(ROOT + ".*.application.port..")
                .and().areTopLevelClasses().and().doNotHaveSimpleName("package-info")
                .should(beContract())
                .because("реализация порта — в адаптере или bootstrap; рядом с интерфейсом лежат только record'ы "
                        + "и исключения из его сигнатуры")
                .check(classes);
    }

    @Test
    @DisplayName("JPA-сущности — только в адаптерах хранения")
    void jpaEntitiesStayInPersistenceAdapters() {
        classes().that().areAnnotatedWith(Entity.class).or().areAnnotatedWith(Embeddable.class)
                .or().areAnnotatedWith(MappedSuperclass.class)
                .should().resideInAPackage(ROOT + ".*.adapter.persistence..")
                .because("строка таблицы — деталь хранения; вне адаптера её увидел бы REST или сценарий")
                .check(classes);
    }

    /** Интерфейс порта или тип из его сигнатуры: сообщение, перечисление, отказ. */
    private static ArchCondition<JavaClass> beContract() {
        return be(describe(
                "an interface, a record, an enum or an exception",
                item -> item.isInterface() || item.isRecord() || item.isEnum()
                        || item.isAssignableTo(Throwable.class)));
    }
}
