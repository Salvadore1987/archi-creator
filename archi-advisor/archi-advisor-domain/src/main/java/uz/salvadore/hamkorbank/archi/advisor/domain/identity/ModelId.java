package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.BaseId;

/** Модель — непрозрачный ключ из modeling. */
public final class ModelId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public ModelId(UUID value) {
        super(value);
    }

    /** Ключ из значения UUID. */
    public static ModelId of(UUID value) {
        return new ModelId(value);
    }

    /** Ключ из строки UUID; неверный формат — {@link IllegalArgumentException}. */
    public static ModelId of(String value) {
        return new ModelId(UUIDUtil.uuid(value));
    }
}
