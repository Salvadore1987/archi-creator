package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ элемента модели. Концом связи служит наравне со связью. */
public record ElementId(UUID value) implements ConceptRef {

    public ElementId {
        Objects.requireNonNull(value, "ElementId");
    }

    public static ElementId of(UUID value) {
        return new ElementId(value);
    }

    public static ElementId of(String value) {
        return new ElementId(UUID.fromString(value));
    }

    public static ElementId next(UuidV7 uuids) {
        return new ElementId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
