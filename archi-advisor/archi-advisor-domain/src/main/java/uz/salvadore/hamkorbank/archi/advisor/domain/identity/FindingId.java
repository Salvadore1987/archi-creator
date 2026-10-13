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

    public static FindingId of(UUID value) {
        return new FindingId(value);
    }

    public static FindingId of(String value) {
        return new FindingId(UUIDUtil.uuid(value));
    }

    public static FindingId next(UuidV7 uuids) {
        return new FindingId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
