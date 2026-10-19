package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ элемента модели. Концом связи служит наравне со связью. */
public final class ElementId extends BaseId<UUID> implements ConceptRef {

    /** Ключ над значением UUID. */
    public ElementId(UUID value) {
        super(value);
    }

    /** Ключ из готового значения. */
    public static ElementId of(UUID value) {
        return new ElementId(value);
    }

    /** Ключ из строкового представления UUID. */
    public static ElementId of(String value) {
        return new ElementId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static ElementId next(UuidV7 uuids) {
        return new ElementId(uuids.next());
    }
}
