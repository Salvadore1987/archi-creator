package uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO помощника по контракту OpenAPI. Отсутствующее значение — {@code null}, а не
 * {@code Optional}: так его видит JSON. Ни один ответ не несёт команды изменения
 * модели — только текст, ссылки и предложения.
 */
public final class Dtos {

    private Dtos() {
    }

    public record ReviewRequest(UUID modelId, UUID viewId) {
    }

    public record ExplainRequest(UUID modelId, String elementId) {
    }

    public record PlanRequest(UUID modelId, String prompt) {
    }

    public record DocumentRequest(UUID modelId, UUID viewId, String elementId) {
    }

    public record ElementRefDto(String archiId, String name) {
    }

    public record FindingDto(UUID id, String severity, String code, String message, String targetKind,
                             ElementRefDto target, String suggestion) {
    }

    public record PlannedElementDto(String archiType, String name, ElementRefDto existingRef) {
    }

    public record PlannedRelationDto(String archiType, String sourceName, String targetName, boolean permitted) {
    }

    public record ModelPlanDto(List<PlannedElementDto> elements, List<PlannedRelationDto> relationships,
                               String notes) {
    }

    public record AnswerDto(String markdown, List<ElementRefDto> references) {
    }

    public record TokenDto(String text) {
    }

    public record SessionMetaDto(UUID sessionId, String outcome, String rejectionReason, long promptTokens,
                                 long completionTokens, boolean contextTruncated) {
    }

    public record StatusDto(UUID workspaceId, boolean enabled, String budgetStatus, String period,
                            LocalDate resetsAt) {
    }

    public record SettingsDto(Boolean enabled, Long monthlyTokenLimit) {
    }

    public record UsageRowDto(String key, long requests, long rejected, long promptTokens, long completionTokens) {
    }

    public record UsageDto(String period, long limitTokens, long consumedTokens, String budgetStatus,
                           List<UsageRowDto> byUser, List<UsageRowDto> byFunction) {
    }
}
