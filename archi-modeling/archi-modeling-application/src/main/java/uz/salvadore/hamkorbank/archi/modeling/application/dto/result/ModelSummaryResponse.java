package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Заголовок модели: имя, описание, состояние, версия Archi, автор и время.
 *
 * @param documentation описание; {@code null} — нет
 * @param status        {@code ACTIVE}, {@code DELETED} или {@code PURGED}
 */
public record ModelSummaryResponse(UUID id, UUID workspaceId, String archiId, String name,
                                   @Nullable String documentation, String status, String archiVersion, String createdBy,
                                   Instant createdAt, Instant updatedAt) {
}
