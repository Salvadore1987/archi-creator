package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.Objects;
import java.util.UUID;

/** Рабочее пространство — непрозрачный ключ из modeling. */
public record WorkspaceId(UUID value) {

    public WorkspaceId {
        Objects.requireNonNull(value, "WorkspaceId");
    }

    /** Ключ из значения UUID. */
    public static WorkspaceId of(UUID value) {
        return new WorkspaceId(value);
    }

    /** Ключ из строки UUID; неверный формат — {@link IllegalArgumentException}. */
    public static WorkspaceId of(String value) {
        return new WorkspaceId(UUIDUtil.uuid(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
