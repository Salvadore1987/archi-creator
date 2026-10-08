package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/** FR-28 и §9.3: роль проверяется на границе use case'а, а не матчером URL. */
class RoleAccessIT extends ApiTest {

    @Test
    @DisplayName("FR-28: VIEWER получает 403 на запись, ARCHITECT — успех, без токена — 401")
    void viewerReadsArchitectWrites() throws Exception {
        mvc.perform(post("/api/v1/models").with(viewer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Чтение\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MDL_ACCESS_DENIED"));

        UUID model = lockedModel(architect(), "Ландшафт");

        mvc.perform(get("/api/v1/models/" + model).with(viewer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.folders.length()").value(9));
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(viewer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/models")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-05: чужую блокировку снимает только ADMIN")
    void onlyAdminForcesRelease() throws Exception {
        UUID model = lockedModel(architect(), "Блокировка");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/models/" + model + "/lock").with(as("someone-else", "ARCHITECT")))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/models/" + model + "/lock").with(admin()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/models/" + model + "/lock").with(viewer())).andExpect(status().isNoContent());
    }
}
