package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ версии модели. */
public record VersionId(UUID value) {

    public VersionId {
        Objects.requireNonNull(value, "VersionId");
    }

    public static VersionId of(UUID value) {
        return new VersionId(value);
    }

    public static VersionId of(String value) {
        return new VersionId(UUID.fromString(value));
    }

    public static VersionId next(UuidV7 uuids) {
        return new VersionId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
