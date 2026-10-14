package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/** Первое пространство заводится при старте на пустой базе — с именем из ресурсов. */
class DefaultWorkspaceIT extends ApiTest {

    @Test
    @DisplayName("Имя первого пространства — текст ресурсов на языке приложения, а не строка конфигурации")
    void defaultWorkspaceNameIsLocalized() throws Exception {
        mvc.perform(get("/api/v1/workspaces").with(viewer()).header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Основное рабочее пространство")));
    }
}
