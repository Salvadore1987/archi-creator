package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdvisorIT extends AdvisorApiTest {

    @Test
    @DisplayName("INV-ADV-005: выключенный помощник закрывает /ai/** с 403 ADVISOR_DISABLED, API не вызывается")
    void disabledWorkspaceReturns403() throws Exception {
        UUID model = lockedModel(architect(), "Помощник выключен");
        jdbc.update("update workspace set ai_enabled = false");

        Response response = ask("review", architect(), "disabled-1", "{\"modelId\":\"" + model + "\"}");

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"ADVISOR_DISABLED\"");
        assertThat(anthropic.requests()).isEmpty();
    }

    @Test
    @DisplayName("INV-ADV-003: повтор с тем же ключом отдаёт результат сессии без второго обращения к API")
    void retryReturnsCachedSession() throws Exception {
        UUID model = lockedModel(architect(), "Повтор ключа");
        anthropic.reply(FakeAnthropic.Reply.text("Модель пуста.").usage(200, 15));
        String body = "{\"modelId\":\"" + model + "\"}";

        Response first = ask("review", architect(), "retry-1", body);
        Response replay = ask("review", architect(), "retry-1", body);

        assertThat(first.status()).isEqualTo(200);
        assertThat(replay.status()).isEqualTo(200);
        assertThat(anthropic.requests()).hasSize(1);
        assertThat(replay.meta()).isEqualTo(first.meta());
        assertThat(jdbc.queryForObject("select consumed_tokens from token_budget", Long.class)).isEqualTo(215L);

        Response conflict = ask("explain", architect(), "retry-1",
                "{\"modelId\":\"" + model + "\",\"elementId\":\"x\"}");
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body()).contains("\"code\":\"INV-ADV-003\"");
    }

    @Test
    @DisplayName("FR-51: исчерпанный бюджет — 429 BUDGET_EXHAUSTED с датой сброса; редактор работает")
    void exhaustedBudgetReturns429WithResetDate() throws Exception {
        UUID model = lockedModel(architect(), "Бюджет");
        jdbc.update("update workspace set ai_monthly_token_limit = 100");
        anthropic.reply(FakeAnthropic.Reply.text("Ответ.").usage(90, 20));
        String body = "{\"modelId\":\"" + model + "\"}";
        assertThat(ask("review", architect(), "budget-1", body).status()).isEqualTo(200);

        Response exhausted = ask("review", architect(), "budget-2", body);

        assertThat(exhausted.status()).isEqualTo(429);
        assertThat(exhausted.body()).contains("\"code\":\"BUDGET_EXHAUSTED\"").contains("\"resetsAt\":\"");
        assertThat(element(architect(), model, "ApplicationComponent", "Редактор работает")).isNotNull();
    }

    @Test
    @DisplayName("Недоступный Anthropic API — 503 ADVISOR_UNAVAILABLE до потока, сессия FAILED")
    void upstreamDownReturns503() throws Exception {
        UUID model = lockedModel(architect(), "API недоступен");
        anthropic.failWith(500);

        Response response = ask("review", architect(), "down-1", "{\"modelId\":\"" + model + "\"}");

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"ADVISOR_UNAVAILABLE\"");
        assertThat(jdbc.queryForObject("select status from advisor_session", String.class)).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("Роли: план недоступен наблюдателю, подсказка доступна")
    void planIsArchitectOnly() throws Exception {
        UUID model = lockedModel(architect(), "Роли помощника");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM");

        Response denied = ask("plan", viewer(), "role-1", "{\"modelId\":\"" + model + "\",\"prompt\":\"Платежи\"}");
        Response explained = ask("explain", viewer(), "role-2",
                "{\"modelId\":\"" + model + "\",\"elementId\":\"" + archiIdOf(crm) + "\"}");

        assertThat(denied.status()).isEqualTo(403);
        assertThat(denied.body()).contains("\"code\":\"ADV_ACCESS_DENIED\"");
        assertThat(explained.status()).isEqualTo(200);
        assertThat(explained.frames("answer")).hasSize(1);
    }

    @Test
    @DisplayName("FR-25: план приходит структурой — существующий элемент найден, связь вне матрицы помечена")
    void planArrivesAsStructure() throws Exception {
        UUID model = lockedModel(architect(), "План");
        element(architect(), model, "ApplicationComponent", "CRM");
        anthropic.reply(new FakeAnthropic.Reply().withText("Идея плана.").withTool("propose_plan",
                "{\"elements\":[{\"archiType\":\"archimate:ApplicationComponent\",\"name\":\"CRM\"},"
                        + "{\"archiType\":\"archimate:BusinessActor\",\"name\":\"Клиент\"}],"
                        + "\"relationships\":[{\"archiType\":\"archimate:CompositionRelationship\","
                        + "\"sourceName\":\"Клиент\",\"targetName\":\"CRM\"}],\"notes\":\"\"}"));

        Response response = ask("plan", architect(), "plan-1",
                "{\"modelId\":\"" + model + "\",\"prompt\":\"Обслуживание клиента\"}");

        assertThat(response.status()).isEqualTo(200);
        String plan = response.frames("plan").getFirst();
        assertThat(plan).contains("\"existingRef\":{\"archiId\":\"").contains("\"permitted\":false");
        assertThat(column("select count(*) from element where model_id = ?", Long.class, model))
                .as("план ничего не создал в модели").containsExactly(1L);
    }
}
