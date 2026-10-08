package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ узла представления. Концом ребра служит наравне с ребром. */
public record ViewNodeId(UUID value) implements ViewEndpoint {

    public ViewNodeId {
        Objects.requireNonNull(value, "ViewNodeId");
    }

    public static ViewNodeId of(UUID value) {
        return new ViewNodeId(value);
    }

    public static ViewNodeId of(String value) {
        return new ViewNodeId(UUID.fromString(value));
    }

    public static ViewNodeId next(UuidV7 uuids) {
        return new ViewNodeId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
