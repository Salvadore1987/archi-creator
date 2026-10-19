package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.BaseId;

/** Идентификатор на UUIDv7, генерируется приложением. */
public final class FindingId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public FindingId(UUID value) {
        super(value);
    }

    /** Новый идентификатор находки. */
    public static FindingId next(UuidV7 uuids) {
        return new FindingId(uuids.next());
    }
}
