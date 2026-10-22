package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Итог сессии — последний кадр потока: исход, расход, было ли усечение контекста.
 *
 * @param rejectionReason причина отказа; {@code null} — сессия не отклонена
 */
public record SessionMetaResponse(UUID sessionId, String outcome, @Nullable String rejectionReason, long promptTokens,
                                  long completionTokens, boolean contextTruncated) {
}
