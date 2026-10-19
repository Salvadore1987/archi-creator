package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;

/** Ключ сессии помощника — UUIDv7 приложения. */
public final class AdvisorSessionId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public AdvisorSessionId(UUID value) {
        super(value);
    }

    /** Ключ из значения UUID. */
    public static AdvisorSessionId of(UUID value) {
        return new AdvisorSessionId(value);
    }

    /** Ключ из строки UUID; неверный формат — {@link IllegalArgumentException}. */
    public static AdvisorSessionId of(String value) {
        return new AdvisorSessionId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static AdvisorSessionId next(UuidV7 uuids) {
        return new AdvisorSessionId(uuids.next());
    }
}
