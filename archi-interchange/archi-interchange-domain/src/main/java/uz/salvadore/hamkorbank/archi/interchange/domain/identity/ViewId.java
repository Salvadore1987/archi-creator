package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Внешний идентификатор modeling: здесь только принимается и передаётся. */
public record ViewId(UUID value) {

    public ViewId {
        Objects.requireNonNull(value, "ViewId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
