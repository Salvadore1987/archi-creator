package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdvisorAuditIT extends AdvisorApiTest {

    @Test
    @DisplayName("INV-ADV-001: отклонённое обращение оставляет запись аудита с причиной")
    void rejectedSessionIsAudited() throws Exception {
        UUID model = lockedModel(architect(), "Аудит отказа");
        jdbc.update("update workspace set ai_enabled = false");

        ask("review", architect(), "audit-rejected", "{\"modelId\":\"" + model + "\"}");

        Map<String, Object> entry = jdbc.queryForMap("select * from ai_audit_log");
        assertThat(entry).containsEntry("outcome", "REJECTED").containsEntry("rejection_reason", "ADVISOR_DISABLED")
                .containsEntry("action", "REVIEW").containsEntry("user_subject", "architect-api")
                .containsEntry("prompt_tokens", 0);
    }

    @Test
    @DisplayName("INV-ADV-001: каждое обращение — ровно одна запись аудита, при любом исходе")
    void everyCallLeavesAuditEntry() throws Exception {
        UUID model = lockedModel(architect(), "Аудит каждого");
        String body = "{\"modelId\":\"" + model + "\"}";
        ask("review", architect(), "audit-ok", body);
        anthropic.failWith(503);
        ask("review", architect(), "audit-failed", body);

        assertThat(column("select outcome from ai_audit_log order by created_at", String.class))
                .containsExactly("COMPLETED", "FAILED");
        assertThat(jdbc.queryForObject("select count(distinct session_id) from ai_audit_log", Long.class))
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("FR-53: расход по пользователям и функциям, отказы — отдельным счётчиком; только администратору")
    void usageBreakdownCountsRejected() throws Exception {
        UUID model = lockedModel(architect(), "Расход");
        anthropic.reply(FakeAnthropic.Reply.text("Ответ.").usage(100, 10));
        ask("review", architect(), "usage-1", "{\"modelId\":\"" + model + "\"}");
        jdbc.update("update workspace set ai_enabled = false");
        ask("review", viewer(), "usage-2", "{\"modelId\":\"" + model + "\"}");

        mvc.perform(get("/api/v1/ai/usage").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.consumedTokens").value(110))
                .andExpect(jsonPath("$.byFunction[0].key").value("REVIEW"))
                .andExpect(jsonPath("$.byFunction[0].requests").value(2))
                .andExpect(jsonPath("$.byFunction[0].rejected").value(1))
                .andExpect(jsonPath("$.byUser.length()").value(2));
        mvc.perform(get("/api/v1/ai/usage").with(architect())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-27: администратор включает помощника и задаёт лимит; состояние видно любой роли")
    void adminTogglesAdvisor() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/ai/settings")
                        .with(admin()).contentType("application/json")
                        .content("{\"enabled\":false,\"monthlyTokenLimit\":2000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monthlyTokenLimit").value(2000));
        mvc.perform(get("/api/v1/ai/status").with(viewer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.budgetStatus").value("OK"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/ai/settings")
                        .with(architect()).contentType("application/json")
                        .content("{\"enabled\":true,\"monthlyTokenLimit\":2000}"))
                .andExpect(status().isForbidden());
    }
}
