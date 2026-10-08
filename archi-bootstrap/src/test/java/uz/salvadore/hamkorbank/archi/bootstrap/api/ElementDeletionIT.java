package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

class ElementDeletionIT extends ApiTest {

    @Test
    @DisplayName("INV-MDL-004: удаление элемента со связями — 409 с перечнем связей; без связей — 204")
    void restrictViolationReturns409() throws Exception {
        UUID model = lockedModel(architect(), "Удаление");
        UUID component = element(architect(), model, "ApplicationComponent", "АБС");
        UUID actor = element(architect(), model, "BusinessActor", "Клиент");
        UUID serving = id(mvc.perform(post("/api/v1/models/" + model + "/relationships").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:ServingRelationship\",\"sourceId\":\"" + component
                                + "\",\"targetId\":\"" + actor + "\"}"))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(delete("/api/v1/elements/" + component).with(architect()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INV-MDL-004"))
                .andExpect(jsonPath("$.relationships[0]").value(serving.toString()));

        mvc.perform(delete("/api/v1/relationships/" + serving).with(architect())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/elements/" + component).with(architect())).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("INV-MDL-007: недопустимая связь — 422 с перечнем допустимых")
    void forbiddenRelationIs422() throws Exception {
        UUID model = lockedModel(architect(), "Матрица");
        UUID component = element(architect(), model, "ApplicationComponent", "АБС");
        UUID actor = element(architect(), model, "BusinessActor", "Клиент");

        mvc.perform(post("/api/v1/models/" + model + "/relationships").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:AssignmentRelationship\",\"sourceId\":\"" + component
                                + "\",\"targetId\":\"" + actor + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RELATION_NOT_PERMITTED"))
                .andExpect(jsonPath("$.permitted").isArray());
    }
}
