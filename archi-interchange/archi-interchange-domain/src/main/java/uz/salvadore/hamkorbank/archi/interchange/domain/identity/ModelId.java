package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Внешний идентификатор modeling: здесь только принимается и передаётся. */
public record ModelId(UUID value) {

    public ModelId {
        Objects.requireNonNull(value, "ModelId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
