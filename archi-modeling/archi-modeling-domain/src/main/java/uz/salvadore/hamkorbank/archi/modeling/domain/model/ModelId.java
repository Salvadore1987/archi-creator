package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ модели — UUIDv7. */
public final class ModelId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public ModelId(UUID value) {
        super(value);
    }

    /** Ключ из готового значения. */
    public static ModelId of(UUID value) {
        return new ModelId(value);
    }

    /** Ключ из строкового представления UUID. */
    public static ModelId of(String value) {
        return new ModelId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static ModelId next(UuidV7 uuids) {
        return new ModelId(uuids.next());
    }
}
