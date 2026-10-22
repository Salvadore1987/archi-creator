package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

class IdempotencyIT extends ApiTest {

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Test
    @DisplayName("INV-MDL-003: тот же ключ и тело — та же модель; другое тело — 409")
    void sameKeyDifferentBodyReturns409() throws Exception {
        String key = "idem-" + IDS.generate();
        UUID first = id(mvc.perform(post("/api/v1/models").with(architect()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ретрай\"}"))
                .andExpect(status().isCreated()).andReturn());
        UUID replay = id(mvc.perform(post("/api/v1/models").with(architect()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ретрай\"}"))
                .andExpect(status().isCreated()).andReturn());

        assertEquals(first, replay);
        mvc.perform(post("/api/v1/models").with(architect()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Другое имя\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INV-MDL-003"));
    }

    @Test
    @DisplayName("INV-MDL-003: повтор сохранения с тем же ключом не создаёт вторую версию")
    void saveReplayDoesNotCreateVersion() throws Exception {
        UUID model = lockedModel(architect(), "Сохранение");
        element(architect(), model, "ApplicationComponent", "АБС");

        mvc.perform(post("/api/v1/models/" + model + "/versions").with(architect()).header("Idempotency-Key", "s-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"первое\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version.versionNo").value(2));
        mvc.perform(post("/api/v1/models/" + model + "/versions").with(architect()).header("Idempotency-Key", "s-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"первое\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version.versionNo").value(2));
        mvc.perform(MockMvcRequestBuilders
                        .get("/api/v1/models/" + model + "/versions").with(viewer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
