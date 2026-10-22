package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Сессия дошла до терминального состояния, расход учтён. Публикуется для всех
 * исходов, включая отказ: аудит и метрики должны видеть отказы. Содержимого
 * ответа здесь нет — событие про факт и стоимость обращения.
 *
 * @param rejectionReason причина отказа; {@code null} — сессия не отклонена
 */
public record AdvisorSessionCompletedEvent(AdvisorSessionId sessionId, WorkspaceId workspaceId, ModelId modelId,
                                      AdvisorFunction function, SessionStatus outcome,
                                      @Nullable RejectionReason rejectionReason, long promptTokens,
                                      long completionTokens, boolean contextTruncated, int findingCount,
                                      long durationMs, String actor, Instant occurredAt)
        implements AdvisorSessionEvent {

    public AdvisorSessionCompletedEvent {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
