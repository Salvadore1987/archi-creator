package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ReviewIT extends AdvisorApiTest {

    @Test
    @DisplayName("INV-ADV-008: находки ревью указывают на существующие объекты, выдуманные цели отброшены")
    void findingsPointToExistingElements() throws Exception {
        UUID model = lockedModel(architect(), "Ревью");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM Creatio");
        String crmId = archiIdOf(crm);
        anthropic.reply(new FakeAnthropic.Reply()
                .withText("Проверяю модель. ")
                .withTool("report_finding", "{\"severity\":\"WARNING\",\"code\":\"ORPHAN_ELEMENT\","
                        + "\"message\":\"Элемент без связей\",\"targetKind\":\"ELEMENT\",\"targetArchiId\":\""
                        + crmId + "\",\"suggestion\":\"Свяжите с сервисом\"}")
                .withTool("report_finding", "{\"severity\":\"ERROR\",\"code\":\"X\",\"message\":\"m\","
                        + "\"targetKind\":\"ELEMENT\",\"targetArchiId\":\"invented-id\",\"suggestion\":\"\"}")
                .withText("Итог: одна проблема.")
                .usage(300, 60));

        Response response = ask("review", architect(), "review-1", "{\"modelId\":\"" + model + "\"}");

        assertThat(response.status()).isEqualTo(200);
        List<String> findings = response.frames("finding");
        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst()).contains("\"archiId\":\"" + crmId + "\"").contains("\"name\":\"CRM Creatio\"");
        assertThat(response.frames("token")).isNotEmpty();
        assertThat(response.meta()).contains("\"outcome\":\"COMPLETED\"").contains("\"promptTokens\":300");
        assertThat(jdbc.queryForObject("select result from advisor_session", String.class)).contains(crmId);
    }

    @Test
    @DisplayName("Ревью представления: в контекст уходят только его элементы")
    void viewReviewSendsViewOnly() throws Exception {
        UUID model = lockedModel(architect(), "Ревью представления");
        UUID placed = element(architect(), model, "ApplicationComponent", "На представлении");
        element(architect(), model, "ApplicationComponent", "Не на представлении");
        UUID view = id(mvc.perform(post("/api/v1/models/" + model + "/views").with(architect())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Обзор\"}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"elementId\":\"" + placed + "\",\"x\":0,\"y\":0}")).andExpect(status().isCreated());

        Response response = ask("review", architect(), "review-view",
                "{\"modelId\":\"" + model + "\",\"viewId\":\"" + view + "\"}");

        assertThat(response.status()).isEqualTo(200);
        assertThat(anthropic.requests().getFirst().body()).contains("На представлении")
                .doesNotContain("Не на представлении");
    }
}
