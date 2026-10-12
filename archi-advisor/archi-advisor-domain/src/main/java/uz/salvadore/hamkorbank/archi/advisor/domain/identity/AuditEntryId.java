package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;

/** Ключ записи аудита — UUIDv7 приложения. */
public record AuditEntryId(UUID value) {

    public AuditEntryId {
        Objects.requireNonNull(value, "AuditEntryId");
    }

    public static AuditEntryId of(UUID value) {
        return new AuditEntryId(value);
    }

    public static AuditEntryId of(String value) {
        return new AuditEntryId(UUID.fromString(value));
    }

    public static AuditEntryId next(UuidV7 uuids) {
        return new AuditEntryId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
