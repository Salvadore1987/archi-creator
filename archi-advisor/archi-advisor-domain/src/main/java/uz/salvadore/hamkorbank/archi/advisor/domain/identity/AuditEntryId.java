package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;

/** Ключ записи аудита — UUIDv7 приложения. */
public final class AuditEntryId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public AuditEntryId(UUID value) {
        super(value);
    }

    /** Ключ из значения UUID. */
    public static AuditEntryId of(UUID value) {
        return new AuditEntryId(value);
    }

    /** Ключ из строки UUID; неверный формат — {@link IllegalArgumentException}. */
    public static AuditEntryId of(String value) {
        return new AuditEntryId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static AuditEntryId next(UuidV7 uuids) {
        return new AuditEntryId(uuids.next());
    }
}
