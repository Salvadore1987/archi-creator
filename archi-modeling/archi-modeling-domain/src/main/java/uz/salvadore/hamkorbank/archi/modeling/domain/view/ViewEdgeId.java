package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ ребра представления. Ребро бывает концом другого ребра. */
public final class ViewEdgeId extends BaseId<UUID> implements ViewEndpoint {

    /** Ключ над значением UUID. */
    public ViewEdgeId(UUID value) {
        super(value);
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
}
