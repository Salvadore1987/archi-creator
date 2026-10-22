package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import uz.salvadore.hamkorbank.archi.bootstrap.advisor.AiSettings;
import uz.salvadore.hamkorbank.archi.bootstrap.git.GitSettings;
import uz.salvadore.hamkorbank.archi.bootstrap.security.CorsSettings;
import uz.salvadore.hamkorbank.archi.bootstrap.security.JwksSettings;
import uz.salvadore.hamkorbank.archi.bootstrap.security.UiSettings;

/** Недопустимая настройка роняет приложение на старте, а не первый запрос. */
class SettingsValidationTest {

    /** Обязательные значения без умолчания в классе: в приложении их даёт {@code application.yaml}. */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AllSettings.class)
            .withPropertyValues("archi.git.cache-dir=/tmp/archi-git",
                    "archi.ui.oidc.authority=http://localhost:8081/realms/archi");

    @Test
    @DisplayName("настройки по умолчанию проходят; у interchange умолчания свои, а не нули")
    void defaultsAreValid() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            InterchangeSettings interchange = context.getBean(InterchangeSettings.class);
            assertEquals(1, interchange.maxParallelImportsPerWorkspace());
            assertEquals(1, interchange.maxParallelRasterizations());
            assertEquals(Duration.ofSeconds(60), interchange.rasterizationWait());
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "archi.interchange.max-parallel-imports-per-workspace=0",
        "archi.interchange.png-max-area-px=-1",
        "archi.web.cors.allowed-origins=*",
        "archi.web.cors.allowed-origins=http://localhost:5173/path",
        "archi.ui.oidc.authority=",
        "archi.ui.oidc.client-id=",
        "archi.ai.min-coverage=2",
        "archi.ai.base-url=api.anthropic.com",
        "archi.modeling.retention-cron=",
        "archi.ai.circuit-breaker.failure-rate-threshold=101",
        "archi.git.committer-name=",
        "archi.git.timeout=500ms",
        "archi.security.jwks-timeout=0s"})
    @DisplayName("недопустимое значение — отказ старта")
    void invalidValueFailsStartup(String property) {
        runner.withPropertyValues(property).run(context -> assertNotNull(context.getStartupFailure(), property));
    }

    @Test
    @DisplayName("каталог кэша Git обязателен; выключенный вход адреса не требует")
    void requiredOnlyWhereMeaningful() {
        new ApplicationContextRunner().withUserConfiguration(AllSettings.class)
                .withPropertyValues("archi.ui.oidc.authority=http://localhost:8081/realms/archi")
                .run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("archi.ui.oidc.enabled=false", "archi.ui.oidc.authority=")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    /** Все классы настроек приложения — так же, как их включают конфигурации. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ModelingSettings.class, InterchangeSettings.class, AiSettings.class,
        GitSettings.class, CorsSettings.class, UiSettings.class, JwksSettings.class})
    static class AllSettings {
    }
}
