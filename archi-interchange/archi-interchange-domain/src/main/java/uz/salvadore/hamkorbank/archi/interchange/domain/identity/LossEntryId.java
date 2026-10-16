package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.BaseId;

/** Идентификатор на UUIDv7, генерируется приложением. */
public final class LossEntryId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public LossEntryId(UUID value) {
        super(value);
    }

    /** Новый идентификатор записи отчёта о потерях. */
    public static LossEntryId next(UuidV7 uuids) {
        return new LossEntryId(uuids.next());
    }
}
