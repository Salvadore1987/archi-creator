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

    /** Запрос ревью: модель и, если задано, одно представление вместо всей модели. */
    public record ReviewRequest(UUID modelId, UUID viewId) {
    }

    /** Запрос подсказки: модель и {@code archiId} элемента. */
    public record ExplainRequest(UUID modelId, String elementId) {
    }

    /** Запрос плана: модель и описание фрагмента архитектуры. */
    public record PlanRequest(UUID modelId, String prompt) {
    }

    /** Запрос документации: по модели, представлению или элементу — не больше одного из двух последних. */
    public record DocumentRequest(UUID modelId, UUID viewId, String elementId) {
    }

    /** Ссылка на объект модели: идентификатор Archi и имя. */
    public record ElementRefDto(String archiId, String name) {
    }

    /** Замечание ревью, кадр потока; {@code suggestion} — совет архитектору, а не команда. */
    public record FindingDto(UUID id, String severity, String code, String message, String targetKind,
                             ElementRefDto target, String suggestion) {
    }

    /** Элемент плана; {@code existingRef} задан, если такой элемент в модели уже есть. */
    public record PlannedElementDto(String archiType, String name, ElementRefDto existingRef) {
    }

    /** Связь плана между элементами по именам; {@code permitted} — допускает ли её матрица связей. */
    public record PlannedRelationDto(String archiType, String sourceName, String targetName, boolean permitted) {
    }

    /** План фрагмента архитектуры — описание, а не изменение модели. */
    public record ModelPlanDto(List<PlannedElementDto> elements, List<PlannedRelationDto> relationships,
                               String notes) {
    }

    /** Ответ подсказки или документации: Markdown и ссылки на упомянутые объекты. */
    public record AnswerDto(String markdown, List<ElementRefDto> references) {
    }

    /** Очередной кусок текста ответа в потоке. */
    public record TokenDto(String text) {
    }

    /** Последний кадр потока: исход сессии, причина отказа и расход токенов. */
    public record SessionMetaDto(UUID sessionId, String outcome, String rejectionReason, long promptTokens,
                                 long completionTokens, boolean contextTruncated) {
    }

    /** Состояние помощника в пространстве: выключатель, состояние бюджета и дата его сброса. */
    public record StatusDto(UUID workspaceId, boolean enabled, String budgetStatus, String period,
                            LocalDate resetsAt) {
    }

    /** Настройки помощника: выключатель и месячный лимит; лимит {@code null} — значение по умолчанию. */
    public record SettingsDto(Boolean enabled, Long monthlyTokenLimit) {
    }

    /** Строка отчёта о расходе: автор или функция и их обращения, отказы и токены. */
    public record UsageRowDto(String key, long requests, long rejected, long promptTokens, long completionTokens) {
    }

    /** Отчёт о расходе за период: лимит, израсходованное и разбивка по авторам и функциям. */
    public record UsageDto(String period, long limitTokens, long consumedTokens, String budgetStatus,
                           List<UsageRowDto> byUser, List<UsageRowDto> byFunction) {
    }
}
