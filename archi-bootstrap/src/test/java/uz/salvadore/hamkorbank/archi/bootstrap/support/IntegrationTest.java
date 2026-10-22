package uz.salvadore.hamkorbank.archi.bootstrap.support;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Основа интеграционных тестов: контекст приложения целиком на {@code postgres:16}
 * в Testcontainers — том же образе, что в docker-compose.yml. Контейнер один
 * на прогон: поднимать базу на каждый класс — минуты ни за что.
 *
 * <p>Ресурс-сервер настроен на JWKS, который не запрашивается при старте: токены
 * в тестах кладёт {@code spring-security-test}, Keycloak не нужен. С настоящим
 * Keycloak — {@code KeycloakIT}.
 *
 * <p>Набор аннотаций — один на все наследники, иначе каждый поднимал бы свой контекст со своим пулом
 * соединений и своим проходом очереди Git: MockMvc включён здесь, хотя нужен не всем, а пул держит одно
 * простаивающее соединение — кэшированных контекстов несколько, база одна.
 *
 * <p>Actuator здесь — на порту приложения: без сервера ({@code MOCK}) отдельный порт не поднимается,
 * и MockMvc эндпоинтов бы не видел. Отдельный порт проверяет {@code ManagementPortIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"management.server.port=", "spring.datasource.hikari.minimum-idle=1"})
@AutoConfigureMockMvc
@ImportTestcontainers(Containers.class)
public abstract class IntegrationTest {

    protected static final EditorIdentity ARCHITECT = EditorIdentity.of("architect-it", Role.ARCHITECT);
    protected static final EditorIdentity OTHER_ARCHITECT = EditorIdentity.of("architect-2", Role.ARCHITECT);
    protected static final EditorIdentity VIEWER = EditorIdentity.of("viewer-it", Role.VIEWER);
    protected static final EditorIdentity ADMIN = new EditorIdentity("admin-it", Set.of(Role.ADMIN), Set.of());

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected WorkspaceApplicationService workspaces;

    /** Часы приложения: время в тестах — то же, по которому сервер считает сроки. */
    @Autowired
    protected Clock clock;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:1/jwks");
        // Без issuer адрес входа для браузера не выводится, а включённый вход без адреса — отказ старта.
        registry.add("archi.ui.oidc.authority", () -> "http://localhost:1/realms/archi");
        registry.add("archi.git.allow-local-repositories", () -> "true");
    }

    protected WorkspaceId workspace() {
        return WorkspaceId.of(workspaces.listWorkspaces().getFirst().id());
    }

    protected <T> List<T> column(String sql, Class<T> type, Object... args) {
        return jdbc.queryForList(sql, type, args);
    }
}
