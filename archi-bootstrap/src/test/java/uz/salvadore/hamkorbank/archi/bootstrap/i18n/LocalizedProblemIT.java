package uz.salvadore.hamkorbank.archi.bootstrap.i18n;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/**
 * Текст отказа — на языке запроса, код — один на всех языках: клиент ветвится
 * по коду, человек читает текст. Без {@code Accept-Language} и для языка, которого
 * нет в ресурсах, — русский: интерфейс продукта русский.
 */
class LocalizedProblemIT extends ApiTest {

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Test
    @DisplayName("UI-021: отказ по-английски при Accept-Language: en, код тот же")
    void englishByAcceptLanguage() throws Exception {
        mvc.perform(get("/api/v1/models/" + IDS.generate()).with(architect())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MDL_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value(Matchers.startsWith("Not found: model ")));
    }

    @Test
    @DisplayName("UI-021: без Accept-Language и для неизвестного языка — по-русски")
    void russianByDefault() throws Exception {
        mvc.perform(get("/api/v1/models/" + IDS.generate()).with(architect()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Не найдено"))
                .andExpect(jsonPath("$.detail").value(Matchers.startsWith("Не найдено: модель ")));
        mvc.perform(get("/api/v1/models/" + IDS.generate()).with(architect())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
                .andExpect(jsonPath("$.title").value("Не найдено"));
    }

    @Test
    @DisplayName("UI-021: заголовок общего отказа и отказа interchange — на языке запроса")
    void titlesFollowAcceptLanguage() throws Exception {
        mvc.perform(get("/api/v1/models/zzz").with(architect()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"));
        mvc.perform(get("/api/v1/models/zzz").with(architect()))
                .andExpect(jsonPath("$.title").value("Некорректный запрос"));
        mvc.perform(put("/api/v1/models").with(architect()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Метод не поддерживается"));

        String workspace = read(mvc.perform(get("/api/v1/workspaces").with(admin())).andReturn(), "$[0].id");
        mvc.perform(get("/api/v1/workspaces/" + workspace + "/git-binding").with(admin())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IXC_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Not found"));
    }
}
