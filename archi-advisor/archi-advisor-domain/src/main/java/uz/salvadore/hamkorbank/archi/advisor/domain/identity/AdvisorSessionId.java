package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;

/** Ключ сессии помощника — UUIDv7 приложения. */
public record AdvisorSessionId(UUID value) {

    public AdvisorSessionId {
        Objects.requireNonNull(value, "AdvisorSessionId");
    }

    public static AdvisorSessionId of(UUID value) {
        return new AdvisorSessionId(value);
    }

    public static AdvisorSessionId of(String value) {
        return new AdvisorSessionId(UUIDUtil.uuid(value));
    }

    public static AdvisorSessionId next(UuidV7 uuids) {
        return new AdvisorSessionId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
