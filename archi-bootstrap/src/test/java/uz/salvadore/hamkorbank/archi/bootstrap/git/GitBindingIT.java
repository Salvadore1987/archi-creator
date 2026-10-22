package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/** Привязка пространства к репозиторию: настраивает только ADMIN, токена в ответе нет. */
class GitBindingIT extends ApiTest {

    private static final String SETTINGS = """
            {"repoUrl":"https://gitlab.example/ea/landscape.git","tokenRef":"env:ARCHI_GIT_TOKEN",
             "authors":[{"subject":"architect-api","name":"Архитектор","email":"architect@example.uz"}]}""";

    @Autowired
    GitBindingRepository bindings;
    @Autowired
    PlatformTransactionManager transactions;

    @AfterEach
    void clean() {
        jdbc.update("delete from git_binding");
    }

    private String binding() {
        return "/api/v1/workspaces/" + workspace().value() + "/git-binding";
    }

    @Test
    @DisplayName("FR-37: ADMIN настраивает привязку — CONFIGURED, ветка по умолчанию main, ссылка на секрет")
    void adminConfiguresBinding() throws Exception {
        mvc.perform(put(binding()).with(admin()).contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIGURED"))
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.tokenRef").value("env:ARCHI_GIT_TOKEN"))
                .andExpect(jsonPath("$.authors[0].email").value("architect@example.uz"));
        mvc.perform(get(binding()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repoUrl").value("https://gitlab.example/ea/landscape.git"));
    }

    @Test
    @DisplayName("FR-37: архитектор привязку не настраивает — 403")
    void architectCannotConfigure() throws Exception {
        mvc.perform(put(binding()).with(architect()).contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("NFR-06: вместо ссылки на секрет сам токен — 422, ни в базу, ни в ответ не попадает")
    void rawTokenIsRejected() throws Exception {
        mvc.perform(put(binding()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(SETTINGS.replace("env:ARCHI_GIT_TOKEN", "glpat-abcdef123456")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IXC_INVALID_REQUEST"))
                .andExpect(content().string(not(containsString("glpat-abcdef123456"))));
        mvc.perform(get(binding()).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-47: снятая привязка — DISABLED, коммиты не трогаются")
    void disabledBindingStays() throws Exception {
        mvc.perform(put(binding()).with(admin()).contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isOk());
        mvc.perform(delete(binding()).with(admin())).andExpect(status().isNoContent());
        mvc.perform(get(binding()).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    @DisplayName("отметка проверки доступности не откатывает голову ветки, записанную публикацией")
    void checkDoesNotOverwriteConcurrentHead() throws Exception {
        mvc.perform(put(binding()).with(admin()).contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isOk());
        WorkspaceId workspace = new WorkspaceId(workspace().value());
        String head = "c".repeat(40);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            GitBinding read = bindings.find(workspace).orElseThrow();
            CompletableFuture.runAsync(() -> jdbc.update(
                    "update git_binding set last_commit_sha = ? where workspace_id = ?", head,
                    workspace.value())).join();
            bindings.save(read.checked(clock.instant()));
        });

        assertEquals(head, jdbc.queryForObject("select last_commit_sha from git_binding where workspace_id = ?",
                String.class, workspace.value()));
    }
}
