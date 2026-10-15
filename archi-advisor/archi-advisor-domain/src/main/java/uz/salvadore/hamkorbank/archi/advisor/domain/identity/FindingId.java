package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;

/** Ключ находки ревью — UUIDv7 приложения. */
public record FindingId(UUID value) {

    public FindingId {
        Objects.requireNonNull(value, "FindingId");
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

    @Override
    public String toString() {
        return value.toString();
    }
}
