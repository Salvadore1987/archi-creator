package uz.salvadore.hamkorbank.archi.modeling.domain.event;

import java.time.Instant;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/** Модель помечена удалённой (soft delete); история версий не затрагивается. */
public record ModelDeleted(ModelId modelId, WorkspaceId workspaceId, String deletedBy, Instant occurredAt)
        implements DomainEvent {

    public ModelDeleted {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(deletedBy, "deletedBy");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
