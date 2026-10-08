package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

class ConcurrentSaveIT extends ApiTest {

    @Test
    @DisplayName("INV-MDL-006, NFR-07: второй писатель получает 409 и видит владельца блокировки")
    void secondWriterGets409() throws Exception {
        UUID model = lockedModel(as("alice", "ARCHITECT"), "Совместная");

        mvc.perform(post("/api/v1/models/" + model + "/lock").with(as("bob", "ARCHITECT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INV-MDL-006"))
                .andExpect(jsonPath("$.lockOwner").value("alice"));
        mvc.perform(post("/api/v1/models/" + model + "/elements").with(as("bob", "ARCHITECT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:ApplicationComponent\",\"name\":\"CRM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INV-MDL-006"));
        mvc.perform(post("/api/v1/models/" + model + "/versions").with(as("bob", "ARCHITECT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"моё\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/models/" + model + "/elements").with(as("alice", "ARCHITECT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:ApplicationComponent\",\"name\":\"CRM\"}"))
                .andExpect(status().isCreated());
    }
}
