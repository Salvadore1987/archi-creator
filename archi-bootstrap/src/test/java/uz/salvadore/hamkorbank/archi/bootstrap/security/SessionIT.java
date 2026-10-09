package uz.salvadore.hamkorbank.archi.bootstrap.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/** Кто вошёл ({@code /me}) и как входить ({@code /ui-config}) — профиль по умолчанию, вход по JWT. */
class SessionIT extends ApiTest {

    @Test
    @DisplayName("FR-28, UI-015: /me — субъект, имя для показа и роли из ROLE_*; без токена — 401")
    void meReflectsAuthentication() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject("u-1").claim("name", "Анна Архитектор")
                                .claim("preferred_username", "anna"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("ROLE_ARCHITECT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("u-1"))
                .andExpect(jsonPath("$.displayName").value("Анна Архитектор"))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("ARCHITECT"))
                .andExpect(jsonPath("$.roles[1]").value("ADMIN"));

        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject("u-2").claim("preferred_username", "boris"))
                        .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(jsonPath("$.displayName").value("boris"))
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"));
        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject("u-3"))))
                .andExpect(jsonPath("$.displayName").value("u-3"))
                .andExpect(jsonPath("$.roles.length()").value(0));

        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-28: /ui-config открыт без токена и называет клиент SPA")
    void uiConfigIsPublic() throws Exception {
        mvc.perform(get("/api/v1/ui-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oidc.clientId").value("archi-creator-ui"))
                .andExpect(jsonPath("$.oidc.authority").exists());
    }
}
