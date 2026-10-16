package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.time.Instant;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Сессия дошла до терминального состояния, расход учтён. Публикуется для всех
 * исходов, включая отказ: аудит и метрики должны видеть отказы. Содержимого
 * ответа здесь нет — событие про факт и стоимость обращения.
 */
public record AdvisorSessionCompletedEvent(AdvisorSessionId sessionId, WorkspaceId workspaceId, ModelId modelId,
                                      AdvisorFunction function, SessionStatus outcome,
                                      Optional<RejectionReason> rejectionReason, long promptTokens,
                                      long completionTokens, boolean contextTruncated, int findingCount,
                                      long durationMs, String actor, Instant occurredAt)
        implements AdvisorSessionEvent {
}
