package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ версии модели. */
public final class VersionId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public VersionId(UUID value) {
        super(value);
    }

    /** Ключ из готового значения. */
    public static VersionId of(UUID value) {
        return new VersionId(value);
    }

    /** Ключ из строкового представления UUID. */
    public static VersionId of(String value) {
        return new VersionId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static VersionId next(UuidV7 uuids) {
        return new VersionId(uuids.next());
    }
}
