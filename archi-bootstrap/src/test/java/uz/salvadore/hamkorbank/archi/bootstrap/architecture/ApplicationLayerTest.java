package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Application-слой по стандарту: сценарий вызывают через входной порт области ({@code port.in}),
 * он принимает команду или запрос и отдаёт результат — record'ы {@code dto}, без доменных типов;
 * работу делает handler сценария. Доменный агрегат за пределы application не выходит.
 *
 * <p>Пока сценарии переводятся по областям, адаптеры ещё зовут старые сервисы — их список
 * {@link #NOT_YET_MIGRATED} сокращается с каждой областью и в конце удаляется вместе с исключением.
 */
class ApplicationLayerTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";
    private static final String APPLICATION = ROOT + ".modeling.application";
    private static final String DOMAIN = ROOT + ".modeling.domain";
    /** Личность вызывающего — параметр входного порта рядом с командой, а не её данные. */
    private static final String ACTOR = DOMAIN + ".common.EditorIdentity";
    /** Сервисы, которые адаптеры ещё зовут напрямую. */
    private static final Set<String> NOT_YET_MIGRATED = Set.of(
            "AccessListService", "CommittedVersion", "ElementService", "LockService", "ModelImportService",
            "ModelLifecycleService", "ModelQueryService", "ModelingKernel", "RelationshipService", "RequestedIds",
            "TreeService", "VersionService", "VersionSnapshot", "ViewService", "WorkspaceService");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("Входной порт modeling не принимает и не отдаёт доменных типов, кроме личности вызывающего")
    void inputPortsExposeNoDomainTypes() {
        methods().that().areDeclaredInClassesThat().resideInAPackage(APPLICATION + ".port.in..")
                .should(new ArchCondition<>("not expose domain types") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        List<JavaType> types = new ArrayList<>(method.getParameterTypes());
                        types.add(method.getReturnType());
                        types.stream().flatMap(ApplicationLayerTest::mentioned)
                                .filter(ApplicationLayerTest::domain).distinct()
                                .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                        method.getFullName() + " exposes " + type.getName())));
                    }
                })
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Команды, запросы и результаты modeling — record'ы в dto по имени и без доменных полей")
    void dtosFollowStandard() {
        classes().that().resideInAPackage(APPLICATION + ".dto.command..").and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Command").andShould().beRecords()
                .allowEmptyShould(true).check(classes);
        classes().that().resideInAPackage(APPLICATION + ".dto.query..").and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Query").andShould().beRecords()
                .allowEmptyShould(true).check(classes);
        classes().that().resideInAPackage(APPLICATION + ".dto.result..").and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Response").andShould().beRecords()
                .allowEmptyShould(true).check(classes);
        classes().that().resideInAPackage(APPLICATION + ".dto..")
                .should(new ArchCondition<>("have no domain-typed fields") {
                    @Override
                    public void check(JavaClass dto, ConditionEvents events) {
                        dto.getFields().stream()
                                .filter(field -> mentioned(field.getType()).anyMatch(ApplicationLayerTest::domain))
                                .forEach(field -> events.add(SimpleConditionEvent.violated(dto,
                                        field.getFullName() + " has a domain type")));
                    }
                })
                .allowEmptyShould(true).check(classes);
    }

    @Test
    @DisplayName("Handler сценария modeling назван по сценарию и лежит в пакете handler")
    void handlersLiveInHandlerPackage() {
        classes().that().haveSimpleNameEndingWith("CommandHandler").or().haveSimpleNameEndingWith("QueryHandler")
                .and().resideInAPackage(APPLICATION + "..")
                .should().resideInAPackage(APPLICATION + ".handler..")
                .allowEmptyShould(true).check(classes);
    }

    @Test
    @DisplayName("Адаптеры зовут сценарии modeling через входной порт, а не сервисы и handler'ы")
    void adaptersUseInputPorts() {
        noClasses().that().resideOutsideOfPackages(APPLICATION + "..", ROOT + ".bootstrap.wiring..")
                .should().dependOnClassesThat(describe("modeling service or handler not yet behind port.in",
                        target -> (target.getPackageName().equals(APPLICATION + ".service")
                                || target.getPackageName().startsWith(APPLICATION + ".handler"))
                                && !NOT_YET_MIGRATED.contains(outermost(target))))
                .check(classes);
    }


    /** Простое имя внешнего класса: у вложенного record'а сервиса — имя сервиса. */
    private static String outermost(JavaClass type) {
        String name = type.getName();
        return name.substring(name.lastIndexOf('.') + 1).split("\\$")[0];
    }

    /** Тип и все типы в его параметрах, включая вложенные и границы подстановок. */
    private static Stream<JavaClass> mentioned(JavaType type) {
        Stream<JavaClass> self = Stream.of(type.toErasure());
        if (type instanceof JavaParameterizedType parameterized) {
            return Stream.concat(self, parameterized.getActualTypeArguments().stream()
                    .flatMap(ApplicationLayerTest::mentioned));
        }
        if (type instanceof JavaWildcardType wildcard) {
            return Stream.concat(wildcard.getUpperBounds().stream(), wildcard.getLowerBounds().stream())
                    .flatMap(ApplicationLayerTest::mentioned);
        }
        return self;
    }

    private static boolean domain(JavaClass type) {
        JavaClass base = type.isArray() ? type.getBaseComponentType() : type;
        return base.getPackageName().startsWith(DOMAIN) && !base.getName().equals(ACTOR);
    }
}
