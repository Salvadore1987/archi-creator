package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;

/** Ключ находки ревью — UUIDv7 приложения. */
public final class FindingId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public FindingId(UUID value) {
        super(value);
    }

    /** Ключ из значения UUID. */
    public static FindingId of(UUID value) {
        return new FindingId(value);
    }

    /** Ключ из строки UUID; неверный формат — {@link IllegalArgumentException}. */
    public static FindingId of(String value) {
        return new FindingId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static FindingId next(UuidV7 uuids) {
        return new FindingId(uuids.next());
    }
}
