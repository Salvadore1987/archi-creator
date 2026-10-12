package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Модель — непрозрачный ключ из modeling. */
public record ModelId(UUID value) {

    public ModelId {
        Objects.requireNonNull(value, "ModelId");
    }

    public static ModelId of(UUID value) {
        return new ModelId(value);
    }

    public static ModelId of(String value) {
        return new ModelId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
