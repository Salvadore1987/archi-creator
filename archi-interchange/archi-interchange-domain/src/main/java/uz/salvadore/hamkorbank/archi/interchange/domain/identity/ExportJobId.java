package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.BaseId;

/** Идентификатор на UUIDv7, генерируется приложением. */
public final class ExportJobId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public ExportJobId(UUID value) {
        super(value);
    }

    /** Новый идентификатор выгрузки. */
    public static ExportJobId next(UuidV7 uuids) {
        return new ExportJobId(uuids.next());
    }
}
