package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Представление — непрозрачный ключ из modeling. */
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

    @Override
    public String toString() {
        return value.toString();
    }
}
