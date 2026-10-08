package uz.salvadore.hamkorbank.archi.modeling.domain.workspace;

import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ рабочего пространства. */
public record WorkspaceId(UUID value) {

    public WorkspaceId {
        Objects.requireNonNull(value, "WorkspaceId");
    }

    public static WorkspaceId of(UUID value) {
        return new WorkspaceId(value);
    }

    public static WorkspaceId of(String value) {
        return new WorkspaceId(UUID.fromString(value));
    }

    public static WorkspaceId next(UuidV7 uuids) {
        return new WorkspaceId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
