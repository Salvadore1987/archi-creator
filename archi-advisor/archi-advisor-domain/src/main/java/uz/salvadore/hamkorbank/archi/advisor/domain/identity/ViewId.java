package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.BaseId;

/** Представление — непрозрачный ключ из modeling. */
public final class ViewId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public ViewId(UUID value) {
        super(value);
    }

    /** Ключ из значения UUID. */
    public static ViewId of(UUID value) {
        return new ViewId(value);
    }

    /** Ключ из строки UUID; неверный формат — {@link IllegalArgumentException}. */
    public static ViewId of(String value) {
        return new ViewId(UUIDUtil.uuid(value));
    }
}
