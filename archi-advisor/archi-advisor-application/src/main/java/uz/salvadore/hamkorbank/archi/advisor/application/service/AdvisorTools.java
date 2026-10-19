package uz.salvadore.hamkorbank.archi.advisor.application.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.advisor.application.port.MetamodelCatalog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.PromptCatalog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.ToolSpec;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.Severity;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TargetKind;

/**
 * Инструменты, через которые модель отдаёт структурированный результат. Схемы
 * строгие: все поля обязательны, лишних нет. Типы плана перечислены в схеме —
 * модель не может предложить тип, которого нет в фазе 1.
 */
final class AdvisorTools {

    static final String REPORT_FINDING = "report_finding";
    static final String PROPOSE_PLAN = "propose_plan";

    private AdvisorTools() {
    }

    static List<ToolSpec> forFunction(AdvisorFunction function, MetamodelCatalog metamodel, PromptCatalog prompts) {
        return switch (function) {
            case REVIEW -> List.of(new ToolSpec(REPORT_FINDING, prompts.toolDescription(REPORT_FINDING), finding()));
            case PLAN -> List.of(new ToolSpec(PROPOSE_PLAN, prompts.toolDescription(PROPOSE_PLAN), plan(metamodel)));
            case EXPLAIN, DOCUMENT -> List.of();
        };
    }

    private static Map<String, Object> finding() {
        return object(Map.of(
                "severity", enumeration(names(Severity.values())),
                "code", Map.of("type", "string"),
                "message", Map.of("type", "string"),
                "targetKind", enumeration(names(TargetKind.values())),
                "targetArchiId", Map.of("type", "string"),
                "suggestion", Map.of("type", "string")));
    }

    private static Map<String, Object> plan(MetamodelCatalog metamodel) {
        Map<String, Object> element = object(Map.of(
                "archiType", enumeration(metamodel.elementTypes()),
                "name", Map.of("type", "string")));
        Map<String, Object> relation = object(Map.of(
                "archiType", enumeration(metamodel.relationshipTypes()),
                "sourceName", Map.of("type", "string"),
                "targetName", Map.of("type", "string")));
        return object(Map.of(
                "elements", Map.of("type", "array", "items", element),
                "relationships", Map.of("type", "array", "items", relation),
                "notes", Map.of("type", "string")));
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties,
                "required", properties.keySet().stream().sorted().toList(),
                "additionalProperties", false);
    }

    private static Map<String, Object> enumeration(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
