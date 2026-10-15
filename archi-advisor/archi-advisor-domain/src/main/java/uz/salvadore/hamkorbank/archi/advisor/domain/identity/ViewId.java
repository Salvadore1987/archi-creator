package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.Objects;
import java.util.UUID;

/** Представление — непрозрачный ключ из modeling. */
public record ViewId(UUID value) {

    public ViewId {
        Objects.requireNonNull(value, "ViewId");
    }

    /** Ключ из значения UUID. */
    public static ViewId of(UUID value) {
        return new ViewId(value);
    }

    /** Ключ из строки UUID; неверный формат — {@link IllegalArgumentException}. */
    public static ViewId of(String value) {
        return new ViewId(UUIDUtil.uuid(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
