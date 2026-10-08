package uz.salvadore.hamkorbank.archi.bootstrap.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Основа тестов REST: MockMvc поверх полного контекста, токен — {@code spring-security-test}
 * с ролями в {@code ROLE_*}, как их отдаёт конвертер Keycloak.
 */
@AutoConfigureMockMvc
public abstract class ApiTest extends IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    protected static RequestPostProcessor as(String subject, String... roles) {
        return jwt().jwt(j -> j.subject(subject)).authorities(
                java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toArray(org.springframework.security.core.GrantedAuthority[]::new));
    }

    protected static RequestPostProcessor architect() {
        return as("architect-api", "ARCHITECT");
    }

    protected static RequestPostProcessor viewer() {
        return as("viewer-api", "VIEWER");
    }

    protected static RequestPostProcessor admin() {
        return as("admin-api", "ADMIN");
    }

    /** Модель с захваченной блокировкой автора — исходная точка правок. */
    protected UUID lockedModel(RequestPostProcessor author, String name) throws Exception {
        UUID model = id(mvc.perform(post("/api/v1/models").with(author).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")).andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(author)).andExpect(status().isOk());
        return model;
    }

    protected UUID element(RequestPostProcessor author, UUID model, String type, String name) throws Exception {
        return id(mvc.perform(post("/api/v1/models/" + model + "/elements").with(author)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archiType\":\"archimate:" + type + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn());
    }

    protected static UUID id(MvcResult result) throws Exception {
        return UUID.fromString(read(result, "$.id"));
    }

    protected static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    protected static List<String> list(String... values) {
        return List.of(values);
    }
}
