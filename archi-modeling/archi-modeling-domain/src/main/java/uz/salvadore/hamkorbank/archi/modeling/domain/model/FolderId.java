package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ папки модели. */
public final class FolderId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public FolderId(UUID value) {
        super(value);
    }

    /** Ключ из готового значения. */
    public static FolderId of(UUID value) {
        return new FolderId(value);
    }

    /** Ключ из строкового представления UUID. */
    public static FolderId of(String value) {
        return new FolderId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static FolderId next(UuidV7 uuids) {
        return new FolderId(uuids.next());
    }
}
