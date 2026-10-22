package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;

/**
 * Направленная связь между концептами одной модели.
 *
 * @param name          имя; {@code null} — не задано
 * @param documentation документация; {@code null} — не задана
 * @param accessType    только у Access; {@code null} — атрибута в файле нет (Archi читает как WRITE)
 * @param directed      только у Association; {@code null} — атрибута нет
 * @param rawXml        остаток XML; {@code null} — нет
 */
public record Relationship(RelationshipId id, FolderId folderId, ArchiId archiId, ArchiType archiType,
                           ConceptRef source, ConceptRef target, @Nullable String name,
                           @Nullable String documentation, @Nullable AccessType accessType,
                           @Nullable Boolean directed, List<PropertyEntry> properties, SortOrder sortOrder,
                           boolean supported, @Nullable RawXml rawXml) {

    public Relationship {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(folderId, "folderId");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sortOrder, "sortOrder");
        if (name != null) {
            Names.limited(name, Names.RELATIONSHIP);
        }
        properties = List.copyOf(properties);
        if (!supported && rawXml == null) {
            throw new InvalidValueException(ModelingMessages.OPAQUE_WITHOUT_RESIDUE, archiId);
        }
        if (source.value().equals(id.value()) || target.value().equals(id.value())) {
            throw new InvalidValueException(ModelingMessages.RELATIONSHIP_SELF_END, archiId);
        }
    }

    /** Служит ли концепт концом этой связи — источником или целью. */
    public boolean touches(ConceptRef concept) {
        return source.equals(concept) || target.equals(concept);
    }

    /**
     * Та же связь с новыми именем, документацией и свойствами; концы и тип не меняются.
     * {@code null} в имени или документации — значения нет.
     */
    public Relationship edited(@Nullable String newName, @Nullable String newDocumentation,
                               List<PropertyEntry> newProperties) {
        return new Relationship(id, folderId, archiId, archiType, source, target, newName, newDocumentation,
                accessType, directed, newProperties, sortOrder, supported, rawXml);
    }

    /** Та же связь в другой папке и на другой позиции. */
    public Relationship movedTo(FolderId folder, SortOrder order) {
        return new Relationship(id, folder, archiId, archiType, source, target, name, documentation, accessType,
                directed, properties, order, supported, rawXml);
    }
}
