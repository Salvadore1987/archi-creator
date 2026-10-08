package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Идентификатор на UUIDv7, генерируется приложением. */
public record LossEntryId(UUID value) {

    public LossEntryId {
        Objects.requireNonNull(value, "LossEntryId");
    }

    public static LossEntryId next(UuidV7 uuids) {
        return new LossEntryId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
