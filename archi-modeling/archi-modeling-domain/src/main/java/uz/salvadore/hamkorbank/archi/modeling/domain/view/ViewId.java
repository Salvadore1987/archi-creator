package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ представления. */
public record ViewId(UUID value) {

    public ViewId {
        Objects.requireNonNull(value, "ViewId");
    }

    public static ViewId of(UUID value) {
        return new ViewId(value);
    }

    public static ViewId of(String value) {
        return new ViewId(UUID.fromString(value));
    }

    public static ViewId next(UuidV7 uuids) {
        return new ViewId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
