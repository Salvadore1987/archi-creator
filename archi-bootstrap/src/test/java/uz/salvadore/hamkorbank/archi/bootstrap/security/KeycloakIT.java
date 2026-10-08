package uz.salvadore.hamkorbank.archi.bootstrap.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.RoundTripGoldenFileTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Containers;

/**
 * Роли модели доступа на настоящем Keycloak 26: realm проекта, токены от Keycloak, проверка
 * подписи ресурс-сервером, роли из {@code realm_access}. Остальные тесты REST кладут
 * токен через {@code spring-security-test}; этот — единственный, где путь от входа
 * до отказа роли пройден целиком.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KeycloakIT {

    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.4")
            .withCopyFileToContainer(MountableFile.forHostPath(
                    RoundTripGoldenFileTest.repositoryFile("deploy/keycloak/archi-realm.json")),
                    "/opt/keycloak/data/import/archi-realm.json")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCommand("start-dev", "--import-realm", "--http-port=8080")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/archi/.well-known/openid-configuration").forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        KEYCLOAK.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Containers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", Containers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", Containers.POSTGRES::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakIT::issuer);
    }

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("FR-28: токен Keycloak — VIEWER читает и получает 403 на запись, ARCHITECT создаёт, без токена 401")
    void rolesFromRealmAccessGuardUseCases() throws Exception {
        String viewer = token("viewer");
        String architect = token("architect");
        String administrator = token("administrator");

        mvc.perform(get("/api/v1/models").header("Authorization", "Bearer " + viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/models").header("Authorization", "Bearer " + viewer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"От читателя\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MDL_ACCESS_DENIED"));
        mvc.perform(post("/api/v1/models").header("Authorization", "Bearer " + architect)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"От архитектора\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + administrator))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + architect))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/models")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/models").header("Authorization", "Bearer " + viewer + "x"))
                .andExpect(status().isUnauthorized());
    }

    private static String issuer() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/archi";
    }

    /** Парольный грант публичного клиента SPA — как вход на этапе 0, только без браузера. */
    private static String token(String user) throws Exception {
        String form = "grant_type=password&client_id=archi-creator-ui&username=" + user + "&password="
                + URLEncoder.encode(user, StandardCharsets.UTF_8);
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                        .uri(URI.create(issuer() + "/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Keycloak не выдал токен " + user + ": " + response.body());
        }
        return JsonPath.read(response.body(), "$.access_token");
    }
}
