package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitBindingSettings;
import uz.salvadore.hamkorbank.archi.interchange.application.mapping.ArchimateSnapshots;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionService;

/**
 * Очистка снимков с настоящим Git: идёт только при доступном репозитории, а очищенный
 * снимок выгружается из коммита так же, как из базы.
 */
class GitRetentionIT extends GitApiTest {

    @Autowired
    ModelingKernel kernel;
    @Autowired
    ArchimateSnapshots snapshots;
    @Autowired
    GitSnapshotArchive git;

    @Test
    @DisplayName("FR-46, FR-47: с доступным Git старые снимки очищаются и выгружаются из коммитов")
    void purgeRunsWithReachableGitAndRestoresFromCommits() throws Exception {
        UUID model = modelWithVersions();
        byte[] first = export(model, 1);

        assertThat(retention().purgeSnapshots()).isEqualTo(2);

        assertThat(snapshotsOf(model)).isEqualTo(1);
        assertThat(export(model, 1)).isEqualTo(first);
    }

    @Test
    @DisplayName("FR-47: репозиторий недоступен — очистка не идёт, привязка UNREACHABLE")
    void purgeStopsWhenRepositoryIsUnreachable() throws Exception {
        UUID model = modelWithVersions();
        bindings.configure(ADMIN, workspace().value(), new GitBindingSettings(
                temp.resolve("gone.git").toUri().toString(), null, "env:ARCHI_GIT_TOKEN_IT", List.of()));

        assertThat(retention().purgeSnapshots()).isZero();

        assertThat(snapshotsOf(model)).isEqualTo(3);
        assertThat(column("select status from git_binding", String.class)).containsExactly("UNREACHABLE");
    }

    /** Три опубликованные версии, «состарившиеся» на полгода. */
    private UUID modelWithVersions() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isOk());
        List<String> ids = read(mvc.perform(get("/api/v1/models/" + model).with(architect())).andReturn(),
                "$.elements[?(@.name == 'АБС')].id");
        for (int i = 2; i <= 3; i++) {
            mvc.perform(patch("/api/v1/elements/" + ids.getFirst()).with(architect())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"АБС " + i + "\"}"))
                    .andExpect(status().isOk());
            save(model, "правка " + i);
        }
        publishing.publishPending();
        assertThat(gitSha(model, 3)).isPresent();
        jdbc.update("update model_version set created_at = created_at - interval '180 days' where model_id = ?",
                model);
        return model;
    }

    private VersionService retention() {
        return new VersionService(kernel, snapshots, snapshots, git, git, ZoneOffset.UTC, true);
    }

    private byte[] export(UUID model, long versionNo) throws Exception {
        return mvc.perform(get("/api/v1/models/" + model + "/export?version=" + versionNo).with(viewer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private int snapshotsOf(UUID model) {
        return jdbc.queryForObject("select count(*) from model_version where model_id = ? and snapshot is not null",
                Integer.class, model);
    }
}
