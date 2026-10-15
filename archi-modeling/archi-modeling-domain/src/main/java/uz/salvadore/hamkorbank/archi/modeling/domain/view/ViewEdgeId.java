package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ ребра представления. Ребро бывает концом другого ребра. */
public record ViewEdgeId(UUID value) implements ViewEndpoint {

    public ViewEdgeId {
        Objects.requireNonNull(value, "ViewEdgeId");
    }

    /** Ключ из готового значения. */
    public static ViewEdgeId of(UUID value) {
        return new ViewEdgeId(value);
    }

    /** Ключ из строкового представления UUID. */
    public static ViewEdgeId of(String value) {
        return new ViewEdgeId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static ViewEdgeId next(UuidV7 uuids) {
        return new ViewEdgeId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
