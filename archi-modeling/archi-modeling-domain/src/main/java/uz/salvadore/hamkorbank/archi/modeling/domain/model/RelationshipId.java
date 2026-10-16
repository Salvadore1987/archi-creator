package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ связи модели. Связь бывает концом другой связи (ассоциация к связи). */
public final class RelationshipId extends BaseId<UUID> implements ConceptRef {

    /** Ключ над значением UUID. */
    public RelationshipId(UUID value) {
        super(value);
    }

    /** Ключ из готового значения. */
    public static RelationshipId of(UUID value) {
        return new RelationshipId(value);
    }

    /** Ключ из строкового представления UUID. */
    public static RelationshipId of(String value) {
        return new RelationshipId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static RelationshipId next(UuidV7 uuids) {
        return new RelationshipId(uuids.next());
    }
}
