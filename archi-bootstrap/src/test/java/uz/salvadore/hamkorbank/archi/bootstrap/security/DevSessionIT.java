package uz.salvadore.hamkorbank.archi.bootstrap.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/** Профиль {@code dev}: входа нет, каждый запрос — от заглушки {@code ARCHITECT}. */
@ActiveProfiles("dev")
class DevSessionIT extends ApiTest {

    @Test
    @DisplayName("FR-28: в dev /ui-config пуст — фронтенд не уводит на вход, /me — заглушка ARCHITECT")
    void devHasNoLogin() throws Exception {
        mvc.perform(get("/api/v1/ui-config"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}", true));
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(DevArchitectAuthenticationFilter.PRINCIPAL))
                .andExpect(jsonPath("$.displayName").value(DevArchitectAuthenticationFilter.PRINCIPAL))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("ARCHITECT"));
    }
}
