package uz.salvadore.hamkorbank.archi.bootstrap.support;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uz.salvadore.hamkorbank.archi.modeling.application.service.WorkspaceService;
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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public abstract class IntegrationTest {

    protected static final EditorIdentity ARCHITECT = EditorIdentity.of("architect-it", Role.ARCHITECT);
    protected static final EditorIdentity OTHER_ARCHITECT = EditorIdentity.of("architect-2", Role.ARCHITECT);
    protected static final EditorIdentity VIEWER = EditorIdentity.of("viewer-it", Role.VIEWER);
    protected static final EditorIdentity ADMIN = new EditorIdentity("admin-it", Set.of(Role.ADMIN), Set.of());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Containers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", Containers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", Containers.POSTGRES::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:1/jwks");
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected WorkspaceService workspaces;

    protected WorkspaceId workspace() {
        return workspaces.list().getFirst().id();
    }

    protected <T> List<T> column(String sql, Class<T> type, Object... args) {
        return jdbc.queryForList(sql, type, args);
    }
}
