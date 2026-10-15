package uz.salvadore.hamkorbank.archi.modeling.domain.workspace;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ рабочего пространства. */
public record WorkspaceId(UUID value) {

    public WorkspaceId {
        Objects.requireNonNull(value, "WorkspaceId");
    }

    /** Ключ из готового значения. */
    public static WorkspaceId of(UUID value) {
        return new WorkspaceId(value);
    }

    /** Ключ из строкового представления UUID. */
    public static WorkspaceId of(String value) {
        return new WorkspaceId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static WorkspaceId next(UuidV7 uuids) {
        return new WorkspaceId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
