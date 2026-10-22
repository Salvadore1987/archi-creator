package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;

/** Правка имени, описания и свойств связи через {@code PATCH /relationships/{id}}. */
class RelationshipEditIT extends ApiTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    private static final String PATCH_BODY = "{\"name\":\"Обслуживает клиента\",\"documentation\":\"Канал — офис\","
            + "\"properties\":[{\"key\":\"sla\",\"value\":\"99.9\"}]}";

    @Test
    @DisplayName("UC-MDL-002: правка связи — 200 с новыми полями, дерево модели показывает их же")
    void patchUpdatesRelationship() throws Exception {
        UUID model = lockedModel(architect(), "Правка связи");
        UUID relationship = serving(model);

        mvc.perform(patch("/api/v1/relationships/" + relationship).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(relationship.toString()))
                .andExpect(jsonPath("$.name").value("Обслуживает клиента"))
                .andExpect(jsonPath("$.documentation").value("Канал — офис"))
                .andExpect(jsonPath("$.properties[0].key").value("sla"))
                .andExpect(jsonPath("$.properties[0].value").value("99.9"));

        MvcResult tree = mvc.perform(get("/api/v1/models/" + model).with(viewer()))
                .andExpect(status().isOk()).andReturn();
        List<String> names = read(tree, "$.relationships[?(@.id == '" + relationship + "')].name");
        assertThat(names).containsExactly("Обслуживает клиента");
    }

    @Test
    @DisplayName("UC-MDL-002: не переданное поле связи не меняется")
    void absentFieldIsKept() throws Exception {
        UUID model = lockedModel(architect(), "Частичная правка связи");
        UUID relationship = serving(model);
        mvc.perform(patch("/api/v1/relationships/" + relationship).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/relationships/" + relationship).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Обслуживает\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Обслуживает"))
                .andExpect(jsonPath("$.documentation").value("Канал — офис"))
                .andExpect(jsonPath("$.properties.length()").value(1));
    }

    @Test
    @DisplayName("FR-28: наблюдатель не правит связь — 403 MDL_ACCESS_DENIED")
    void viewerIsDenied() throws Exception {
        UUID model = lockedModel(architect(), "Связь наблюдателя");
        UUID relationship = serving(model);

        mvc.perform(patch("/api/v1/relationships/" + relationship).with(viewer())
                        .contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ModelingCodes.ACCESS_DENIED));
    }

    @Test
    @DisplayName("Неизвестная связь — 404 MDL_NOT_FOUND")
    void unknownRelationshipIsNotFound() throws Exception {
        mvc.perform(patch("/api/v1/relationships/" + IDS.generate()).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ModelingCodes.NOT_FOUND));
    }

    @Test
    @DisplayName("INV-MDL-006: правка связи без блокировки модели — 409")
    void patchWithoutLockIsConflict() throws Exception {
        UUID model = lockedModel(architect(), "Связь без блокировки");
        UUID relationship = serving(model);
        mvc.perform(delete("/api/v1/models/" + model + "/lock").with(architect()))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(patch("/api/v1/relationships/" + relationship).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ModelingCodes.LOCK_REQUIRED));
    }

    private UUID serving(UUID model) throws Exception {
        UUID component = element(architect(), model, "ApplicationComponent", "АБС");
        UUID actor = element(architect(), model, "BusinessActor", "Клиент");
        return id(mvc.perform(post("/api/v1/models/" + model + "/relationships").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:ServingRelationship\",\"sourceId\":\"" + component
                                + "\",\"targetId\":\"" + actor + "\"}"))
                .andExpect(status().isCreated()).andReturn());
    }
}
