package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Идентификатор на UUIDv7, генерируется приложением. */
public record ImportSessionId(UUID value) {

    public ImportSessionId {
        Objects.requireNonNull(value, "ImportSessionId");
    }

    public static ImportSessionId next(UuidV7 uuids) {
        return new ImportSessionId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
