package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Идентификатор на UUIDv7, генерируется приложением. */
public record ExportJobId(UUID value) {

    public ExportJobId {
        Objects.requireNonNull(value, "ExportJobId");
    }

    public static ExportJobId next(UuidV7 uuids) {
        return new ExportJobId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
