package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

/**
 * Контракт и код описывают один API: каждый «метод + путь» трёх контрактов OpenAPI
 * обслуживает обработчик, и у каждого обработчика под {@code /api/v1} есть строка контракта.
 * Имена переменных пути не сравниваются — только их места.
 */
class OpenApiContractTest {

    private static final String ROOT = "uz.salvadore.hamkorbank.archi";
    private static final Path CONTRACTS = Path.of("../spec/contracts");
    private static final List<String> CONTEXTS = List.of("modeling", "interchange", "advisor");
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Test
    @DisplayName("ADR-0022: множество «метод + путь» контрактов OpenAPI равно множеству обработчиков")
    void contractsMatchControllers() throws IOException {
        Set<String> contract = contract();
        Set<String> handlers = handlers();
        Set<String> unserved = new TreeSet<>(contract);
        unserved.removeAll(handlers);
        Set<String> undocumented = new TreeSet<>(handlers);
        undocumented.removeAll(contract);

        assertEquals(Set.of(), unserved, "в контракте, но без обработчика");
        assertEquals(Set.of(), undocumented, "обработчик без строки в контракте");
        assertEquals(65, contract.size(), "сколько операций в трёх контрактах");
    }

    private static Set<String> contract() throws IOException {
        Set<String> operations = new TreeSet<>();
        for (String context : CONTEXTS) {
            try (Reader reader = Files.newBufferedReader(CONTRACTS.resolve(context).resolve("rest-api.openapi.yaml"))) {
                Map<String, Object> document = new Yaml().load(reader);
                String base = String.valueOf(((Map<?, ?>) ((List<?>) document.get("servers")).getFirst()).get("url"));
                ((Map<?, ?>) document.get("paths")).forEach((path, item) -> ((Map<?, ?>) item).keySet().stream()
                        .map(String::valueOf).filter(METHODS::contains)
                        .forEach(method -> operations.add(operation(method, base + path))));
            }
        }
        return operations;
    }

    private static Set<String> handlers() {
        Set<String> operations = new TreeSet<>();
        for (JavaClass type : new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT)) {
            if (!type.isAnnotatedWith(RestController.class)) {
                continue;
            }
            Class<?> controller = type.reflect();
            RequestMapping prefix = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String base = prefix == null || prefix.path().length == 0 ? "" : prefix.path()[0];
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String path = base + (mapping.path().length == 0 ? "" : mapping.path()[0]);
                for (RequestMethod verb : mapping.method()) {
                    operations.add(operation(verb.name(), path));
                }
            }
        }
        return operations;
    }

    private static String operation(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + path.replaceAll("\\{[^}]+}", "{}");
    }
}
