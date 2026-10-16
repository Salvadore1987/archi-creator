package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.BaseId;

/** Рабочее пространство — непрозрачный ключ из modeling. */
public final class WorkspaceId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public WorkspaceId(UUID value) {
        super(value);
    }

    /** Ключ из значения UUID. */
    public static WorkspaceId of(UUID value) {
        return new WorkspaceId(value);
    }

    /** Ключ из строки UUID; неверный формат — {@link IllegalArgumentException}. */
    public static WorkspaceId of(String value) {
        return new WorkspaceId(UUIDUtil.uuid(value));
    }
}
