package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

/**
 * Выгрузка в Git через базу: сохранение → outbox → коммит, повтор без правок
 * без diff, история только дописывается, очищенный снимок возвращается из коммита.
 */
class GitExportIT extends GitApiTest {

    @Test
    @DisplayName("FR-40, INV-IXC-011: модель, вернувшаяся к прежнему содержимому, даёт то же дерево — без diff")
    void repeatedExportProducesNoDiff() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));
        publishing.publishPending();
        String element = elementNamed(model, "АБС");

        rename(model, element, "АБС «Ядро»");
        save(model, "переименование");
        rename(model, element, "АБС");
        save(model, "обратно");
        publishing.publishPending();

        List<RevCommit> history = history();
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getTree()).isEqualTo(history.get(2).getTree());
        assertThat(history.get(1).getTree()).isNotEqualTo(history.get(2).getTree());
        mvc.perform(post("/api/v1/models/" + model + "/git/push").with(architect()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SYNCED"))
                .andExpect(jsonPath("$.versionNo").value(3))
                .andExpect(jsonPath("$.gitSha").value(history.get(0).name()));
        assertThat(history()).hasSize(3);
    }

    @Test
    @DisplayName("INV-IXC-009: история только дописывается — каждая версия поверх предыдущей, git_sha в версии")
    void historyIsAppendOnly() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));
        publishing.publishPending();
        RevCommit first = history().getFirst();
        rename(model, elementNamed(model, "АБС"), "АБС-2");
        save(model, "вторая");
        publishing.publishPending();

        List<RevCommit> history = history();
        assertThat(history).hasSize(2);
        assertThat(history.getFirst().getParent(0)).isEqualTo(first);
        assertThat(gitSha(model, 1)).contains(first.name());
        assertThat(gitSha(model, 2)).contains(history.getFirst().name());
        assertThat(history.getFirst().getFullMessage()).startsWith("вторая")
                .contains("Archi-Model-Id: " + model).contains("Archi-Version: 2");
        assertThat(history.getFirst().getAuthorIdent().getEmailAddress()).isEqualTo("architect@bank.uz");
    }

    @Test
    @DisplayName("FR-46: очищенный снимок возвращается из коммита версии — выгрузка та же, что до очистки")
    void purgedSnapshotIsRestoredFromGit() throws Exception {
        UUID model = importModel(Fixtures.reference());
        publishing.publishPending();
        byte[] before = export(model);

        jdbc.update("update model_version set snapshot = null where model_id = ? and version_no = 1", model);

        assertThat(export(model)).isEqualTo(before);
    }

    @Test
    @DisplayName("FR-47: без привязки очищенный снимок вернуть неоткуда — 410")
    void purgedSnapshotWithoutGitIsGone() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));
        publishing.publishPending();
        jdbc.update("update model_version set snapshot = null where model_id = ? and version_no = 1", model);
        jdbc.update("delete from git_binding");

        mvc.perform(get("/api/v1/models/" + model + "/export?version=1").with(viewer()))
                .andExpect(status().isGone());
    }

    private byte[] export(UUID model) throws Exception {
        return mvc.perform(get("/api/v1/models/" + model + "/export?version=1").with(viewer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private String elementNamed(UUID model, String name) throws Exception {
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isOk());
        List<String> ids = read(mvc.perform(get("/api/v1/models/" + model).with(architect())).andReturn(),
                "$.elements[?(@.name == '" + name + "')].id");
        return ids.getFirst();
    }

    private void rename(UUID model, String element, String name) throws Exception {
        mvc.perform(patch("/api/v1/elements/" + element).with(architect()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")).andExpect(status().isOk());
    }
}
