package uz.salvadore.hamkorbank.archi.modeling.domain.workspace;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ рабочего пространства. */
public final class WorkspaceId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public WorkspaceId(UUID value) {
        super(value);
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
}
