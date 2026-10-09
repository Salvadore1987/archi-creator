package uz.salvadore.hamkorbank.archi.bootstrap.i18n;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/**
 * Текст отказа — на языке запроса, код — один на всех языках: клиент ветвится
 * по коду, человек читает текст. Без {@code Accept-Language} и для языка, которого
 * нет в ресурсах, — русский: интерфейс продукта русский.
 */
class LocalizedProblemIT extends ApiTest {

    @Test
    @DisplayName("UI-021: отказ по-английски при Accept-Language: en, код тот же")
    void englishByAcceptLanguage() throws Exception {
        mvc.perform(get("/api/v1/models/" + UUID.randomUUID()).with(architect())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MDL_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Not found: model ")));
    }

    @Test
    @DisplayName("UI-021: без Accept-Language и для неизвестного языка — по-русски")
    void russianByDefault() throws Exception {
        mvc.perform(get("/api/v1/models/" + UUID.randomUUID()).with(architect()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Не найдено"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Не найдено: модель ")));
        mvc.perform(get("/api/v1/models/" + UUID.randomUUID()).with(architect())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
                .andExpect(jsonPath("$.title").value("Не найдено"));
    }
}
