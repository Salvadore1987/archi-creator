package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.BaseId;

/** Идентификатор на UUIDv7, генерируется приложением. */
public final class ImportSessionId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public ImportSessionId(UUID value) {
        super(value);
    }

    /** Новый идентификатор сессии импорта. */
    public static ImportSessionId next(UuidV7 uuids) {
        return new ImportSessionId(uuids.next());
    }
}
