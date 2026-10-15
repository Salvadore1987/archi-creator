package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Внутренний ключ связи модели. Связь бывает концом другой связи (ассоциация к связи). */
public record RelationshipId(UUID value) implements ConceptRef {

    public RelationshipId {
        Objects.requireNonNull(value, "RelationshipId");
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

    @Override
    public String toString() {
        return value.toString();
    }
}
