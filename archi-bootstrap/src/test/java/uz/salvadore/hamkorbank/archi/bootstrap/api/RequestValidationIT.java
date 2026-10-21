package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;

/**
 * Форма запроса проверяется до сценария: поле не задано или элемент списка {@code null} —
 * {@code 422} с кодом неверного ввода контекста и путём поля, а не {@code 500} из глубины.
 * Объекты не нужны: проверка формы срабатывает раньше поиска.
 */
class RequestValidationIT extends ApiTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Test
    @DisplayName("UC-MDL-007: null в списке доступа — 422 MDL_INVALID_INPUT с позицией элемента")
    void nullAccessEntry() throws Exception {
        mvc.perform(put("/api/v1/models/" + IDS.generate() + "/acl").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("[null]"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ModelingCodes.INVALID_INPUT))
                .andExpect(jsonPath("$.detail").value("Не задано поле [0]"));
    }

    @Test
    @DisplayName("UC-MDL-003: свойство без ключа — 422 с путём поля, а не 500")
    void propertyWithoutKey() throws Exception {
        mvc.perform(patch("/api/v1/elements/" + IDS.generate()).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"properties\":[{\"value\":\"x\"}]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ModelingCodes.INVALID_INPUT))
                .andExpect(jsonPath("$.detail").value("Не задано поле properties[0].key"));
    }

    @Test
    @DisplayName("UC-MDL-008: null среди узлов раскладки и ребро без концов — 422 с перечнем полей")
    void nullLayoutNode() throws Exception {
        mvc.perform(put("/api/v1/views/" + IDS.generate() + "/layout").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nodes\":[null]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Не задано поле nodes[0]"));
        mvc.perform(post("/api/v1/views/" + IDS.generate() + "/edges").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ModelingCodes.INVALID_INPUT))
                .andExpect(jsonPath("$.detail").value("Не заданы поля: relationshipId, sourceId, targetId"));
    }

    @Test
    @DisplayName("UC-IXC-003: null среди авторов привязки к Git — 422 IXC_INVALID_REQUEST")
    void nullGitAuthor() throws Exception {
        mvc.perform(put("/api/v1/workspaces/" + IDS.generate() + "/git-binding").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://git.example/r.git\",\"tokenRef\":\"env:T\","
                                + "\"authors\":[null]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(InterchangeCodes.INVALID_REQUEST))
                .andExpect(jsonPath("$.detail").value("Не задано поле «authors[0]»"));
    }

    @Test
    @DisplayName("UC-ADV-001: обращение к помощнику без модели — 422 ADV_INVALID_INPUT")
    void advisorWithoutModel() throws Exception {
        mvc.perform(post("/api/v1/ai/review").with(architect()).header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(AdvisorCodes.INVALID_INPUT))
                .andExpect(jsonPath("$.detail").value("Не задано поле modelId"));
    }
}
