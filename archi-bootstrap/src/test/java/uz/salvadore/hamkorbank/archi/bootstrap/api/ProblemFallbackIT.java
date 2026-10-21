package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.wiring.ErrorCodes;

/**
 * Последняя линия обработчика ошибок: отказ, который не перевёл ни один контекст, всё равно
 * уходит {@code problem+json} с кодом. Отдельный контекст — с обработчиком, который падает.
 */
class ProblemFallbackIT extends ApiTest {

    /** Обработчик с дефектом — сбой, которого никто не ждёт. */
    @RestController
    static class FailingController {

        @GetMapping("/api/v1/test/failure")
        String fail() {
            throw new IllegalStateException("секрет из глубин сервера");
        }
    }

    @TestConfiguration
    static class FailingEndpoint {

        @Bean
        FailingController failingController() {
            return new FailingController();
        }
    }

    @Test
    @DisplayName("ADR-0022: неожиданный сбой — 500 INTERNAL_ERROR в problem+json, без подробностей")
    void unexpectedFailureIsProblem() throws Exception {
        mvc.perform(get("/api/v1/test/failure").with(architect()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCodes.INTERNAL_ERROR))
                .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("секрет"))))
                .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("IllegalState"))));
    }

    @Test
    @DisplayName("ADR-0022: метод не поддержан — 405 с Allow и кодом в problem+json")
    void methodNotAllowedIsProblem() throws Exception {
        mvc.perform(put("/api/v1/models").with(architect()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists(HttpHeaders.ALLOW))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCodes.REQUEST_NOT_SUPPORTED));
    }

    @Test
    @DisplayName("ADR-0022: тело не того типа — 415 с кодом в problem+json")
    void unsupportedMediaTypeIsProblem() throws Exception {
        mvc.perform(post("/api/v1/models/import").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(ErrorCodes.REQUEST_NOT_SUPPORTED));
    }

    @Test
    @DisplayName("ADR-0022: неверный UUID в пути — 400 с именем параметра, без типов Java")
    void unreadableParameterNamesParameter() throws Exception {
        mvc.perform(get("/api/v1/models/zzz").with(architect()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value(
                        "Bad request: the value of the parameter «id» could not be read"));
    }

    @Test
    @DisplayName("ADR-0022: неверный UUID в теле — 400 с путём поля, без текста парсера")
    void unreadableFieldNamesPath() throws Exception {
        mvc.perform(post("/api/v1/models").with(architect()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"М\",\"workspaceId\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Некорректный запрос: значение поля «workspaceId» не разобрано"));
        mvc.perform(post("/api/v1/models").with(architect()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("java"))))
                .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("JSON parse"))));
    }

    @Test
    @DisplayName("FR-28: запрос без входа по-прежнему 401 — отказ безопасности последняя линия не глотает")
    void unauthenticatedStays401() throws Exception {
        mvc.perform(get("/api/v1/models")).andExpect(status().isUnauthorized());
    }
}
