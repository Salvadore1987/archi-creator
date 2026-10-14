package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

/**
 * Слияние предложения из ветки через API, базу и настоящий JGit: три состояния —
 * опубликованная версия, правки в базе и ветка merge request'а.
 */
class MergeRequestIT extends GitApiTest {

    @Test
    @DisplayName("INV-IXC-010: конфликт разрешается по полю, непересекающаяся правка сливается сама")
    void conflictIsResolvedPerField() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));
        publishing.publishPending();
        lock(model);
        RevCommit proposal = externalCommit("proposal", text -> text
                .replace("\nname: АБС\n", "\nname: АБС «Предложение»\n")
                .replace("\nname: HamkorMobile\n", "\nname: HamkorMobile 2\n"));
        rename(model, "АБС", "АБС «API»");
        save(model, "наша правка");

        MvcResult report = preview(model, "proposal", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.commitSha").value(proposal.name()))
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].field").value("name"))
                .andExpect(jsonPath("$.conflicts[0].base").value("АБС"))
                .andExpect(jsonPath("$.conflicts[0].ours").value("АБС «API»"))
                .andExpect(jsonPath("$.conflicts[0].theirs").value("АБС «Предложение»"))
                .andExpect(jsonPath("$.changes[?(@.name == 'HamkorMobile 2')]").isNotEmpty())
                .andReturn();
        String conflict = read(report, "$.conflicts[0].id");
        String mergeBase = read(report, "$.mergeBaseSha");
        String baseHash = read(report, "$.baseHash");
        assertThat(versions(model)).as("отчёт версий не создаёт").containsExactly(1L, 2L);

        merge(model, proposal.name(), mergeBase, baseHash, "{}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IXC_MERGE_CONFLICTS_UNRESOLVED"))
                .andExpect(jsonPath("$.conflicts[0]").value(conflict));
        assertThat(versions(model)).containsExactly(1L, 2L);

        String choice = "{\"" + conflict + "\":\"THEIRS\"}";
        preview(model, "proposal", choice).andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.conflicts[0].resolution").value("THEIRS"));
        merge(model, proposal.name(), mergeBase, baseHash, choice).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.versionNo").value(3))
                .andExpect(jsonPath("$.created").value(true));

        mvc.perform(get("/api/v1/models/" + model).with(architect()))
                .andExpect(jsonPath("$.elements[?(@.name == 'АБС «Предложение»')]").isNotEmpty())
                .andExpect(jsonPath("$.elements[?(@.name == 'HamkorMobile 2')]").isNotEmpty());
        assertThat(column("select merged_sha from git_model_merge where model_id = ?", String.class, model))
                .containsExactly(proposal.name());
        assertThat(column("select comment from model_version where model_id = ? and version_no = 3", String.class,
                model)).containsExactly("Слияние из Git: proposal (" + proposal.name().substring(0, 7) + ")");
    }

    @Test
    @DisplayName("FR-39, INV-IXC-009: слитая ветка предложения — второй родитель коммита публикации")
    void disjointProposalIsMergedAndPublished() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));
        publishing.publishPending();
        lock(model);
        RevCommit proposal = externalCommit("proposal",
                text -> text.replace("\nname: HamkorMobile\n", "\nname: HamkorMobile 2\n"));
        rename(model, "АБС", "АБС «API»");
        save(model, "наша правка");
        publishing.publishPending();

        MvcResult report = preview(model, "proposal", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.conflicts.length()").value(0))
                .andExpect(jsonPath("$.marks[?(@.mark == 'CHANGED')]").isNotEmpty())
                .andReturn();
        merge(model, proposal.name(), read(report, "$.mergeBaseSha"), read(report, "$.baseHash"), "{}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(3));
        publishing.publishPending();

        List<RevCommit> history = history();
        RevCommit top = history.getFirst();
        assertThat(top.getParentCount()).isEqualTo(2);
        assertThat(top.getParent(1)).isEqualTo(proposal);
        assertThat(gitSha(model, 3)).contains(top.name());
        assertThat(new String(modelDirectory(top).entrySet().stream()
                .filter(e -> e.getKey().startsWith("elements/")).map(e -> new String(e.getValue()))
                .reduce("", String::concat).getBytes())).contains("name: HamkorMobile 2", "name: АБС «API»");
        mvc.perform(post("/api/v1/models/" + model + "/git/push").with(architect())).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SYNCED"));
    }

    @Test
    @DisplayName("UC-IXC-007: модель без опубликованной версии — 409, VIEWER — 403")
    void refusals() throws Exception {
        UUID model = importModel(Fixtures.fixture("styled_objects"));

        preview(model, "main", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IXC_GIT_NO_MERGE_BASE"));
        mvc.perform(post("/api/v1/models/" + model + "/git/merge/preview").with(viewer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ref\":\"main\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions preview(UUID model, String ref, String resolutions) throws Exception {
        return mvc.perform(post("/api/v1/models/" + model + "/git/merge/preview").with(architect())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ref\":\"" + ref + "\",\"resolutions\":" + resolutions + "}"));
    }

    private ResultActions merge(UUID model, String sha, String mergeBase, String baseHash, String resolutions)
            throws Exception {
        return mvc.perform(post("/api/v1/models/" + model + "/git/merge").with(architect())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ref\":\"proposal\",\"commitSha\":\"" + sha + "\",\"mergeBaseSha\":\"" + mergeBase
                        + "\",\"baseHash\":\"" + baseHash + "\",\"resolutions\":" + resolutions + "}"));
    }
}
