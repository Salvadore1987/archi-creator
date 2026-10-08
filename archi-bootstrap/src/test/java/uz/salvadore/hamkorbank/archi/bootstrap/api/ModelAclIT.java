package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

class ModelAclIT extends ApiTest {

    @Test
    @DisplayName("INV-MDL-011: модель со списком доступа скрыта от чужих — 404 и нет в списке; группа видит")
    void hiddenModelAnswers404() throws Exception {
        UUID model = lockedModel(as("owner", "ARCHITECT"), "Закрытая");
        mvc.perform(put("/api/v1/models/" + model + "/acl").with(as("owner", "ARCHITECT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"principalType\":\"USER\",\"principal\":\"owner\",\"access\":\"WRITE\"},"
                                + "{\"principalType\":\"GROUP\",\"principal\":\"audit\",\"access\":\"READ\"}]"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/models/" + model).with(as("stranger", "ARCHITECT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/models").with(as("stranger", "ARCHITECT")))
                .andExpect(jsonPath("$[?(@.id == '" + model + "')]").isEmpty());

        var auditor = jwt().jwt(j -> j.subject("auditor").claim("groups", List.of("/audit")))
                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"));
        mvc.perform(get("/api/v1/models/" + model).with(auditor)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/models/" + model).with(admin())).andExpect(status().isOk());
    }
}
