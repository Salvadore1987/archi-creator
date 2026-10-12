package uz.salvadore.hamkorbank.archi.advisor.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

class ExplainElementTest {

    @Test
    @DisplayName("FR-24: допустимые связи в подсказке берутся из матрицы, а не из догадки модели")
    void allowedRelationsComeFromMatrix() {
        Fixture fixture = new Fixture();

        fixture.service().ask(Fixture.ALICE, AdvisorRequest.explain(Fixture.MODEL, "crm"), "k", Locale.ENGLISH)
                .run(new Fixture.Recorded());

        String user = fixture.upstreamCalls.getFirst().user();
        assertTrue(user.contains("\"permittedRelations\":{"));
        assertTrue(user.contains("\"" + Fixture.REALIZATION + "\":[\"" + Fixture.SERVICE + "\"]"));
        assertTrue(user.contains("\"focus\":\"crm\""));
        assertTrue(fixture.upstreamCalls.getFirst().tools().isEmpty(), "подсказка — текст, без инструментов");
    }

    @Test
    @DisplayName("INV-ADV-008: ссылки ответа — только на объекты модели, имя — из модели")
    void answerReferencesExistingObjectsOnly() {
        Fixture fixture = new Fixture();
        fixture.script = listener -> {
            listener.usage(new TokenUsage(50, 0));
            listener.text("Компонент [CRM](archi:crm) реализует [сервис](archi:svc); ");
            listener.text("[выдумка](archi:nowhere) не существует.");
        };
        Fixture.Recorded recorded = new Fixture.Recorded();

        fixture.service().ask(Fixture.ALICE, AdvisorRequest.explain(Fixture.MODEL, "crm"), "k", Locale.ENGLISH)
                .run(recorded);

        assertEquals(List.of("crm", "svc"), recorded.answer.references().stream().map(r -> r.archiId()).toList());
        assertEquals("Карточка клиента", recorded.answer.references().get(1).name());
        assertEquals(recorded.text.toString(), recorded.answer.markdown());
    }

    @Test
    @DisplayName("FR-25: план фильтрует типы вне фазы 1 и помечает связь, недопустимую по матрице")
    void planIsCheckedAgainstMetamodel() {
        Fixture fixture = new Fixture();
        fixture.script = listener -> {
            listener.usage(new TokenUsage(80, 30));
            listener.toolCall("propose_plan", Map.of(
                    "elements", List.of(
                            Map.of("archiType", Fixture.APP, "name", "CRM"),
                            Map.of("archiType", Fixture.PROCESS, "name", "Выдача карты"),
                            Map.of("archiType", "archimate:Goal", "name", "Рост")),
                    "relationships", List.of(
                            Map.of("archiType", Fixture.SERVING, "sourceName", "CRM", "targetName", "Выдача карты"),
                            Map.of("archiType", Fixture.REALIZATION, "sourceName", "Выдача карты",
                                    "targetName", "CRM")),
                    "notes", ""));
        };
        Fixture.Recorded recorded = new Fixture.Recorded();

        fixture.service().ask(Fixture.ALICE, AdvisorRequest.plan(Fixture.MODEL, "Выдача карты"), "k", Locale.ENGLISH)
                .run(recorded);

        assertEquals(List.of(Fixture.APP, Fixture.PROCESS), recorded.plan.elementTypes());
        assertEquals("crm", recorded.plan.elements().getFirst().existingRef().orElseThrow().archiId(),
                "существующий элемент найден по имени и типу");
        assertTrue(recorded.plan.relationships().get(0).permitted());
        assertFalse(recorded.plan.relationships().get(1).permitted());
        assertTrue(fixture.upstreamCalls.getFirst().user().contains("\"request\":\"Выдача карты\""));
    }

    @Test
    @DisplayName("FR-25: модель не вызвала инструмент плана — сессия неуспешна, расход списан")
    void planWithoutToolCallFails() {
        Fixture fixture = new Fixture();
        Fixture.Recorded recorded = new Fixture.Recorded();

        fixture.service().ask(Fixture.ALICE, AdvisorRequest.plan(Fixture.MODEL, "Что-нибудь"), "k", Locale.ENGLISH)
                .run(recorded);

        assertEquals("FAILED", recorded.meta.outcome().name());
        assertEquals(110, fixture.budgets.values().iterator().next().consumedTokens());
    }
}
