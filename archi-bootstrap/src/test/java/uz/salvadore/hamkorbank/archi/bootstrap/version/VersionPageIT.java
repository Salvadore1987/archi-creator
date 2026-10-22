package uz.salvadore.hamkorbank.archi.bootstrap.version;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;

/** История версий отдаётся страницами: каждое сохранение — версия, и без предела ответ рос бы вечно. */
class VersionPageIT extends ApiTest {

    @Test
    @DisplayName("FR-06: страница истории — size версий от новых, before продолжает к старым")
    void historyIsPaged() throws Exception {
        UUID model = lockedModel(architect(), "Страницы");
        for (int i = 2; i <= 4; i++) {
            mvc.perform(patch("/api/v1/models/" + model).with(architect()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Страницы " + i + "\"}")).andExpect(status().isOk());
            mvc.perform(post("/api/v1/models/" + model + "/versions").with(architect())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isCreated());
        }
        String versions = "/api/v1/models/" + model + "/versions";

        mvc.perform(get(versions).with(viewer())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].versionNo", Matchers.contains(4, 3, 2, 1)));
        mvc.perform(get(versions + "?size=2").with(viewer())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].versionNo", Matchers.contains(4, 3)));
        mvc.perform(get(versions + "?size=2&before=3").with(viewer())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].versionNo", Matchers.contains(2, 1)));
        mvc.perform(get(versions + "?before=1").with(viewer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(versions + "?size=0").with(viewer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ModelingCodes.INVALID_INPUT));
    }
}
