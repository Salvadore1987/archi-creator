package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Рабочее пространство — непрозрачный ключ из modeling. */
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

    @Override
    public String toString() {
        return value.toString();
    }
}
