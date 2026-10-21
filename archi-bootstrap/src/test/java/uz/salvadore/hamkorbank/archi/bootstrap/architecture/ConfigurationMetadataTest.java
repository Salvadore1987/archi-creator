package uz.salvadore.hamkorbank.archi.bootstrap.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Процессор конфигурации подключён к сборке: у каждого класса настроек есть группа в метаданных,
 * по которым IDE подсказывает ключи {@code archi.*}, а опечатка в ключе видна до запуска.
 */
class ConfigurationMetadataTest {

    private static final String METADATA = "META-INF/spring-configuration-metadata.json";

    @Test
    @DisplayName("у каждого @ConfigurationProperties есть группа в метаданных конфигурации")
    void everySettingsClassHasMetadata() throws IOException {
        List<String> prefixes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("uz.salvadore.hamkorbank.archi").stream()
                .filter(c -> c.isAnnotatedWith(ConfigurationProperties.class))
                .map(ConfigurationMetadataTest::prefix)
                .toList();
        assertFalse(prefixes.isEmpty(), "классы настроек не найдены");

        // Метаданные лежат и в jar'ах Spring Boot: группы собираются со всех файлов classpath.
        Set<String> groups = new HashSet<>();
        for (URL file : Collections.list(getClass().getClassLoader().getResources(METADATA))) {
            try (InputStream in = file.openStream()) {
                for (JsonNode group : JsonMapper.builder().build().readTree(in).path("groups")) {
                    groups.add(group.path("name").asString());
                }
            }
        }

        for (String prefix : prefixes) {
            assertTrue(groups.contains(prefix), "нет метаданных группы " + prefix + ": процессор конфигурации "
                    + "не подключён к сборке");
        }
    }

    private static String prefix(JavaClass settings) {
        ConfigurationProperties annotation = settings.getAnnotationOfType(ConfigurationProperties.class);
        return annotation.prefix().isEmpty() ? annotation.value() : annotation.prefix();
    }
}
