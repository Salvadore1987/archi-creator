package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ ребра представления. Ребро бывает концом другого ребра. */
public record ViewEdgeId(UUID value) implements ViewEndpoint {

    public ViewEdgeId {
        Objects.requireNonNull(value, "ViewEdgeId");
    }

    public static ViewEdgeId of(UUID value) {
        return new ViewEdgeId(value);
    }

    public static ViewEdgeId of(String value) {
        return new ViewEdgeId(UUID.fromString(value));
    }

    public static ViewEdgeId next(UuidV7 uuids) {
        return new ViewEdgeId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
