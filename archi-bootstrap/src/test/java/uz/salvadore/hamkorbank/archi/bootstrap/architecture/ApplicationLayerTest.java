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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Application-слой по стандарту: сценарий вызывают через входной порт области ({@code port.in}),
 * он принимает команду или запрос и отдаёт результат — record'ы {@code dto}, без доменных типов;
 * работу делает handler сценария. Доменный агрегат за пределы application не выходит.
 */
class ApplicationLayerTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";
    private static final String MODELING = ROOT + ".modeling.application";
    private static final String INTERCHANGE = ROOT + ".interchange.application";
    /** Личность вызывающего — параметр входного порта рядом с командой, а не её данные. */
    private static final String ACTOR = ROOT + ".modeling.domain.common.EditorIdentity";
    /**
     * Вход импорта interchange несёт агрегаты, разложенные из документа на его стороне: перевод
     * этого входа в DTO идёт вместе с раскладкой документа при переводе interchange.
     */
    private static final Set<String> INTERCHANGE_INPUT = Set.of(MODELING + ".port.ModelContent");
    /**
     * Сценарии interchange, ещё не переведённые на входные порты: адаптеры пока зовут их напрямую.
     * Git переводится отдельным этапом; остальное уходит из списка по мере перевода.
     */
    private static final Set<String> NOT_YET_MIGRATED = Set.of(
            INTERCHANGE + ".git.GitBindingService",
            INTERCHANGE + ".git.GitBindingSettings",
            INTERCHANGE + ".git.GitImportService",
            INTERCHANGE + ".git.GitMergeService",
            INTERCHANGE + ".git.GitPublishingService",
            INTERCHANGE + ".mapping.ArchimateSnapshots");
    /**
     * Что адаптеру interchange можно кроме входного порта, его DTO и выходных портов: отказы контекста
     * (их переводит обработчик ошибок) и визуальный язык канвы — данные, которые {@code bootstrap}
     * читает из classpath и отдаёт сценариям картинок.
     */
    private static final Set<String> INTERCHANGE_ADAPTER_SUPPORT = Set.of(
            INTERCHANGE + ".InterchangeException",
            INTERCHANGE + ".ExportDefectException",
            INTERCHANGE + ".rendering.Notation");

    private static JavaClasses classes;

    /** Контекст с переведённым application-слоем: его пакет и пакеты доменов, которые порт не отдаёт. */
    record Context(String name, String application, List<String> domains) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Context> contexts() {
        return Stream.of(
                new Context("modeling", MODELING, List.of(ROOT + ".modeling.domain")),
                new Context("interchange", INTERCHANGE, List.of(ROOT + ".interchange.domain",
                        ROOT + ".modeling.domain")));
    }

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contexts")
    @DisplayName("Входной порт не принимает и не отдаёт доменных типов и типов выходных портов")
    void inputPortsExposeNoDomainTypes(Context context) {
        methods().that().areDeclaredInClassesThat().resideInAPackage(context.application() + ".port.in..")
                .should(new ArchCondition<>("not expose domain types") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        List<JavaType> types = new ArrayList<>(method.getParameterTypes());
                        types.add(method.getReturnType());
                        types.stream().flatMap(ApplicationLayerTest::mentioned)
                                .filter(type -> domain(context, type) || outputPort(type)).distinct()
                                .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                        method.getFullName() + " exposes " + type.getName())));
                    }
                })
                .allowEmptyShould(true)
                .check(classes);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contexts")
    @DisplayName("Команды, запросы и результаты — record'ы в dto по имени и без доменных полей")
    void dtosFollowStandard(Context context) {
        String application = context.application();
        classes().that().resideInAPackage(application + ".dto.command..").and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Command").andShould().beRecords()
                .allowEmptyShould(true).check(classes);
        classes().that().resideInAPackage(application + ".dto.query..").and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Query").andShould().beRecords()
                .allowEmptyShould(true).check(classes);
        classes().that().resideInAPackage(application + ".dto.result..").and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Response").andShould().beRecords()
                .allowEmptyShould(true).check(classes);
        classes().that().resideInAPackage(application + ".dto..")
                .should(new ArchCondition<>("have no domain-typed fields") {
                    @Override
                    public void check(JavaClass dto, ConditionEvents events) {
                        dto.getFields().stream()
                                .filter(field -> mentioned(field.getType()).anyMatch(type -> domain(context, type)))
                                .forEach(field -> events.add(SimpleConditionEvent.violated(dto,
                                        field.getFullName() + " has a domain type")));
                    }
                })
                .allowEmptyShould(true).check(classes);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contexts")
    @DisplayName("Handler сценария назван по сценарию и лежит в пакете handler")
    void handlersLiveInHandlerPackage(Context context) {
        classes().that().haveSimpleNameEndingWith("CommandHandler").or().haveSimpleNameEndingWith("QueryHandler")
                .and().resideInAPackage(context.application() + "..")
                .should().resideInAPackage(context.application() + ".handler..")
                .allowEmptyShould(true).check(classes);
    }

    @Test
    @DisplayName("Адаптеры зовут сценарии modeling через входной порт, а не сервисы и handler'ы")
    void adaptersUseInputPorts() {
        noClasses().that().resideOutsideOfPackages(MODELING + "..", ROOT + ".bootstrap.wiring..")
                .should().dependOnClassesThat(describe("modeling service or handler behind port.in",
                        target -> target.getPackageName().equals(MODELING + ".service")
                                || target.getPackageName().startsWith(MODELING + ".handler")))
                .check(classes);
    }

    @Test
    @DisplayName("Адаптеры берут из application interchange только входной порт, его DTO и выходные порты")
    void adaptersUseInterchangeInputPorts() {
        noClasses().that().resideOutsideOfPackages(INTERCHANGE + "..", ROOT + ".bootstrap.wiring..")
                .should().dependOnClassesThat(describe("interchange application class behind port.in",
                        ApplicationLayerTest::interchangeInternal))
                .check(classes);
    }

    /** Класс application interchange, который адаптеру не положен: не порт, не DTO и не поимённое исключение. */
    private static boolean interchangeInternal(JavaClass target) {
        String packageName = target.getPackageName();
        if (!packageName.startsWith(INTERCHANGE)) {
            return false;
        }
        String topLevel = target.getName().split("\\$")[0];
        return !packageName.startsWith(INTERCHANGE + ".port.in") && !packageName.startsWith(INTERCHANGE + ".dto")
                && !packageName.equals(INTERCHANGE + ".port") && !INTERCHANGE_ADAPTER_SUPPORT.contains(topLevel)
                && !NOT_YET_MIGRATED.contains(topLevel);
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

    /** Тип выходного порта: за ним хранение и агрегаты, сценарий наружу его не отдаёт и не принимает. */
    private static boolean outputPort(JavaClass type) {
        JavaClass base = type.isArray() ? type.getBaseComponentType() : type;
        return (base.getPackageName().equals(MODELING + ".port") || base.getPackageName().equals(INTERCHANGE + ".port"))
                && !INTERCHANGE_INPUT.contains(base.getName());
    }

    private static boolean domain(Context context, JavaClass type) {
        JavaClass base = type.isArray() ? type.getBaseComponentType() : type;
        return context.domains().stream().anyMatch(base.getPackageName()::startsWith)
                && !base.getName().equals(ACTOR);
    }
}
