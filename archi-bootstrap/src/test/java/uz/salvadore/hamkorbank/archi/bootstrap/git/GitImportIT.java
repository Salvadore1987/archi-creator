package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

/**
 * Импорт состояния из Git по ссылке через API и базу: отчёт ничего не меняет,
 * применение — новая версия, отказ оставляет только сессию {@code REJECTED}.
 */
class GitImportIT extends GitApiTest {

    @Test
    @DisplayName("INV-IXC-012: отчёт до применения и без записи; применение — новая версия; повтор отчёта — 409")
    void reportPrecedesApply() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));
        publishing.publishPending();
        lock(model);
        RevCommit proposal = externalCommit("proposal", text -> text.replace("\nname: АБС\n", "\nname: АБС «Ядро»\n"));
        byte[] before = export(model);

        MvcResult report = preview(model, "proposal").andExpect(status().isOk())
                .andExpect(jsonPath("$.refKind").value("BRANCH"))
                .andExpect(jsonPath("$.commitSha").value(proposal.name()))
                .andExpect(jsonPath("$.baseVersionNo").value(1))
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.unchanged").value(false))
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].kind").value("ELEMENT"))
                .andExpect(jsonPath("$.changes[0].change").value("CHANGED"))
                .andExpect(jsonPath("$.changes[0].name").value("АБС «Ядро»"))
                .andExpect(jsonPath("$.changes[0].fields[0]").value("name"))
                .andReturn();
        String baseHash = read(report, "$.baseHash");

        assertThat(versions(model)).as("отчёт версий не создаёт").containsExactly(1L);
        assertThat(export(model)).isEqualTo(before);
        assertThat(sessions(model)).as("и сессий тоже").isEmpty();

        pull(model, "proposal", proposal.name(), baseHash, "принять предложение").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.created").value(true));

        assertThat(versions(model)).containsExactly(1L, 2L);
        assertThat(column("select comment from model_version where model_id = ? and version_no = 2", String.class,
                model)).containsExactly("принять предложение");
        mvc.perform(get("/api/v1/models/" + model).with(architect()))
                .andExpect(jsonPath("$.elements[?(@.name == 'АБС «Ядро»')]").isNotEmpty());
        assertThat(sessions(model)).containsExactly("APPLIED");
        assertThat(column("select source_name from import_session where target_model_id = ?", String.class, model))
                .containsExactly("proposal@" + proposal.name());

        pull(model, "proposal", proposal.name(), baseHash, "ещё раз").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INV-IXC-012"));
        assertThat(versions(model)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("INV-IXC-012: отказ применения (нет блокировки) — модель не тронута, в журнале сессия REJECTED")
    void failedApplyLeavesOnlyRejectedSession() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));
        publishing.publishPending();
        RevCommit proposal = externalCommit("proposal", text -> text.replace("\nname: АБС\n", "\nname: АБС-2\n"));
        String baseHash = read(preview(model, "proposal").andExpect(status().isOk()).andReturn(), "$.baseHash");
        byte[] before = export(model);

        pull(model, "proposal", proposal.name(), baseHash, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INV-MDL-006"));

        assertThat(versions(model)).containsExactly(1L);
        assertThat(export(model)).isEqualTo(before);
        assertThat(sessions(model)).containsExactly("REJECTED");
        assertThat(column("select f.code from import_finding f join import_session s on s.id = f.session_id "
                + "where s.target_model_id = ?", String.class, model)).containsExactly("INV-MDL-006");
    }

    @Test
    @DisplayName("FR-37, UC-IXC-006: импорт головы ветки снимает расхождение, публикация идёт дальше fast-forward")
    void importOfHeadResolvesDivergence() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));
        publishing.publishPending();
        lock(model);
        RevCommit foreign = externalCommit("main", text -> text.replace("\nname: АБС\n", "\nname: АБС «Git»\n"));
        rename(model, "АБС", "АБС «API»");
        save(model, "правка мимо ветки");
        publishing.publishPending();
        assertThat(gitSha(model, 2)).as("чужая правка каталога — расхождение").isEmpty();

        String baseHash = read(preview(model, "main").andExpect(status().isOk())
                .andExpect(jsonPath("$.baseVersionNo").value(2))
                .andExpect(jsonPath("$.changes[0].name").value("АБС «Git»")).andReturn(), "$.baseHash");
        pull(model, "main", foreign.name(), baseHash, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(3));

        assertThat(column("select status from outbox_event where partition_key = ? "
                + "and (payload ->> 'versionNo')::bigint = 2", String.class, model)).containsExactly("SKIPPED");
        assertThat(column("select last_commit_sha from git_binding", String.class)).containsExactly(foreign.name());
        publishing.publishPending();
        assertThat(gitSha(model, 3)).as("версия из головы получает голову").contains(foreign.name());
        assertThat(history()).hasSize(2);
        mvc.perform(post("/api/v1/models/" + model + "/git/push").with(architect())).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SYNCED"));

        rename(model, "АБС «Git»", "АБС «Ядро»");
        save(model, "после импорта");
        publishing.publishPending();

        List<RevCommit> history = history();
        assertThat(history).hasSize(3);
        assertThat(history.getFirst().getParent(0)).isEqualTo(foreign);
        assertThat(gitSha(model, 4)).contains(history.getFirst().name());
    }

    @Test
    @DisplayName("UC-IXC-006: неизвестная ссылка — 404, пустая — 422, VIEWER — 403")
    void refusals() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));
        publishing.publishPending();

        preview(model, "nope").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IXC_GIT_REF_NOT_FOUND"));
        preview(model, " ").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IXC_INVALID_REQUEST"));
        mvc.perform(post("/api/v1/models/" + model + "/git/pull/preview").with(viewer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ref\":\"main\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions preview(UUID model, String ref) throws Exception {
        return mvc.perform(post("/api/v1/models/" + model + "/git/pull/preview").with(architect())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ref\":\"" + ref + "\"}"));
    }

    private ResultActions pull(UUID model, String ref, String sha, String baseHash, String comment) throws Exception {
        String body = "{\"ref\":\"" + ref + "\",\"commitSha\":\"" + sha + "\",\"baseHash\":\"" + baseHash + "\""
                + (comment == null ? "" : ",\"comment\":\"" + comment + "\"") + "}";
        return mvc.perform(post("/api/v1/models/" + model + "/git/pull").with(architect())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void rename(UUID model, String from, String to) throws Exception {
        List<String> ids = read(mvc.perform(get("/api/v1/models/" + model).with(architect())).andReturn(),
                "$.elements[?(@.name == '" + from + "')].id");
        mvc.perform(patch("/api/v1/elements/" + ids.getFirst()).with(architect())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + to + "\"}"))
                .andExpect(status().isOk());
    }

    private void lock(UUID model) throws Exception {
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isOk());
    }

    private List<Long> versions(UUID model) {
        return column("select version_no from model_version where model_id = ? order by version_no", Long.class,
                model);
    }

    private List<String> sessions(UUID model) {
        return column("select status from import_session where target_model_id = ? order by started_at", String.class,
                model);
    }

    private byte[] export(UUID model) throws Exception {
        return mvc.perform(get("/api/v1/models/" + model + "/export").with(viewer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    /** Правка YAML модели не через сервер: коммит в ветку {@code branch} поверх головы {@code main}. */
    protected RevCommit externalCommit(String branch, UnaryOperator<String> edit) throws Exception {
        Path work = Files.createTempDirectory(temp, "work");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(work.toFile()).call()) {
            if (!branch.equals("main")) {
                git.checkout().setCreateBranch(true).setName(branch).call();
            }
            boolean changed = false;
            try (var files = Files.walk(work.resolve("models"))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".yaml")).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    String edited = edit.apply(text);
                    if (!edited.equals(text)) {
                        Files.writeString(file, edited, StandardCharsets.UTF_8);
                        changed = true;
                    }
                }
            }
            assertThat(changed).as("правка нашла, что менять").isTrue();
            git.add().addFilepattern("models").call();
            RevCommit commit = git.commit().setMessage("правка YAML руками")
                    .setAuthor(new PersonIdent("Ревьюер", "review@bank.uz"))
                    .setCommitter(new PersonIdent("Ревьюер", "review@bank.uz")).call();
            git.push().setRemote("origin").add(branch).call();
            return commit;
        }
    }
}
