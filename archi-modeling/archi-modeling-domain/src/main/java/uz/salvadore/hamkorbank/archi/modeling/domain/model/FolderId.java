package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ папки модели. */
public record FolderId(UUID value) {

    public FolderId {
        Objects.requireNonNull(value, "FolderId");
    }

    public static FolderId of(UUID value) {
        return new FolderId(value);
    }

    public static FolderId of(String value) {
        return new FolderId(UUID.fromString(value));
    }

    public static FolderId next(UuidV7 uuids) {
        return new FolderId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
