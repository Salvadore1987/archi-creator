package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Идентификатор на UUIDv7, генерируется приложением. */
public record FindingId(UUID value) {

    public FindingId {
        Objects.requireNonNull(value, "FindingId");
    }

    public static FindingId next(UuidV7 uuids) {
        return new FindingId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
