package uz.salvadore.hamkorbank.archi.modeling.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Сохранение создало версию — единственная точка, после которой изменения считаются
 * зафиксированными (UC-MDL-004).
 *
 * @param author subject из JWT, без персональных данных
 */
public record ModelVersionCommitted(ModelId modelId, WorkspaceId workspaceId, long versionNo, String author,
                                    Optional<String> comment, int elementCount, int relationshipCount,
                                    Instant occurredAt) implements DomainEvent {

    public ModelVersionCommitted {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
