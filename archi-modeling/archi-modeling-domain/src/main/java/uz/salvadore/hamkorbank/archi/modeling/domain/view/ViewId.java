package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ представления. */
public record ViewId(UUID value) {

    public ViewId {
        Objects.requireNonNull(value, "ViewId");
    }

    /** Ключ из готового значения. */
    public static ViewId of(UUID value) {
        return new ViewId(value);
    }

    /** Ключ из строкового представления UUID. */
    public static ViewId of(String value) {
        return new ViewId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static ViewId next(UuidV7 uuids) {
        return new ViewId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
