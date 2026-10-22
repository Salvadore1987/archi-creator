package uz.salvadore.hamkorbank.archi.modeling.domain.event;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Сохранение создало версию — единственная точка, после которой изменения считаются
 * зафиксированными.
 *
 * @param author  subject из JWT, без персональных данных
 * @param comment комментарий версии; {@code null} — не задан
 */
public record ModelVersionCommittedEvent(ModelId modelId, WorkspaceId workspaceId, long versionNo, String author,
                                    @Nullable String comment, int elementCount, int relationshipCount,
                                    Instant occurredAt) implements ModelVersionEvent {

    public ModelVersionCommittedEvent {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
