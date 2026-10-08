package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Внешний идентификатор modeling: здесь только принимается и передаётся. */
public record WorkspaceId(UUID value) {

    public WorkspaceId {
        Objects.requireNonNull(value, "WorkspaceId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
