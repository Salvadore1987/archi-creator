package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

class ModelLifecycleIT extends ApiTest {

    @Test
    @DisplayName("INV-MDL-002: PURGE только из DELETED и только ADMIN; удалённая не правится и не выгружается")
    void purgeRequiresDeletedState() throws Exception {
        UUID model = lockedModel(architect(), "Жизненный цикл");

        mvc.perform(post("/api/v1/models/" + model + "/purge").with(admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INV-MDL-002"));
        mvc.perform(delete("/api/v1/models/" + model).with(architect())).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/models/" + model + "/export").with(viewer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODEL_DELETED"));
        mvc.perform(post("/api/v1/models/" + model + "/purge").with(architect())).andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/models/" + model + "/purge").with(admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/models/" + model).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("UC-MDL-001: удалённую модель восстанавливает ADMIN")
    void adminRestoresDeleted() throws Exception {
        UUID model = lockedModel(architect(), "Восстановление");
        mvc.perform(delete("/api/v1/models/" + model).with(architect())).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/models/" + model + "/restore").with(architect())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/models/" + model + "/restore").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
