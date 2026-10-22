package uz.salvadore.hamkorbank.archi.bootstrap.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;

/**
 * Actuator на настоящих портах: пробы и метрики — на своём порту под той же защитой, на порту приложения
 * Actuator нет. Порт Actuator берётся из настроек приложения; тест на случайных портах подменяет его
 * случайным, но только если он отличается от порта приложения.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ManagementPortIT extends IntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private HealthEndpointGroups groups;

    @Test
    @DisplayName("Actuator — на своём порту, а не на порту приложения")
    void actuatorOnOwnPort() throws Exception {
        assertThat(managementPort).isNotEqualTo(serverPort);
        HttpResponse<String> health = get(managementPort, "/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
        HttpResponse<String> onServer = get(serverPort, "/actuator/health");
        assertThat(onServer.statusCode()).isNotEqualTo(200);
        assertThat(onServer.body()).doesNotContain("UP");
    }

    @Test
    @DisplayName("на порту Actuator метрики по-прежнему закрыты токеном")
    void metricsStillProtected() throws Exception {
        assertThat(get(managementPort, "/actuator/prometheus").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("готовность смотрит на базу, живучесть — только на само приложение")
    void readinessIncludesDatabase() {
        assertThat(groups.get("readiness").isMember("db")).isTrue();
        assertThat(groups.get("liveness").isMember("db")).isFalse();
    }

    @Test
    @DisplayName("/actuator/info называет версию сборки")
    void infoCarriesBuild() throws Exception {
        HttpResponse<String> info = get(managementPort, "/actuator/info");
        assertThat(info.statusCode()).isEqualTo(200);
        assertThat(info.body()).contains("\"build\"").contains("\"version\":\"1.0-SNAPSHOT\"");
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
