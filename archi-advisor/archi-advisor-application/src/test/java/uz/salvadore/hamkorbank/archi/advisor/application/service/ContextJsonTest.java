package uz.salvadore.hamkorbank.archi.advisor.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;

class ContextJsonTest {

    @Test
    @DisplayName("Контекст — компактный JSON без пробелов, строки экранированы")
    void compactAndEscaped() {
        String json = ContextJson.model(Fixture.graph().whole(AdvisorFunction.REVIEW));

        assertEquals("{\"elements\":[{\"id\":\"crm\",\"type\":\"archimate:ApplicationComponent\",\"name\":\"CRM\","
                + "\"doc\":\"Клиентская система\"},{\"id\":\"svc\",\"type\":\"archimate:ApplicationService\","
                + "\"name\":\"Карточка клиента\"},{\"id\":\"onboarding\",\"type\":\"archimate:BusinessProcess\","
                + "\"name\":\"Онбординг\"}],\"relationships\":[{\"id\":\"r1\","
                + "\"type\":\"archimate:RealizationRelationship\",\"source\":\"crm\",\"target\":\"svc\"},"
                + "{\"id\":\"r2\",\"type\":\"archimate:ServingRelationship\",\"source\":\"svc\","
                + "\"target\":\"onboarding\"}],\"views\":[]}", json);
        assertEquals("\"a\\\"b\\nc\\\\\"", ContextJson.quote("a\"b\nc\\"));
    }

    @Test
    @DisplayName("Служебная часть: вложенные объекты, списки, готовый JSON вставляется как есть")
    void objectWithRaw() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("task", "EXPLAIN");
        fields.put("truncated", false);
        fields.put("list", List.of("a", "b"));
        fields.put("model", new ContextJson.Raw("{}"));

        assertEquals("{\"task\":\"EXPLAIN\",\"truncated\":false,\"list\":[\"a\",\"b\"],\"model\":{}}",
                ContextJson.object(fields));
    }
}
