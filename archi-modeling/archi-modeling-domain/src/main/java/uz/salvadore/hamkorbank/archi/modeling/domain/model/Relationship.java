package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;

/**
 * Направленная связь между концептами одной модели (INV-MDL-004).
 *
 * @param accessType только у Access; пусто — атрибута в файле нет (Archi читает как WRITE)
 * @param directed   только у Association
 */
public record Relationship(RelationshipId id, FolderId folderId, ArchiId archiId, ArchiType archiType,
                           ConceptRef source, ConceptRef target, Optional<String> name,
                           Optional<String> documentation, Optional<AccessType> accessType,
                           Optional<Boolean> directed, List<PropertyEntry> properties, SortOrder sortOrder,
                           boolean supported, Optional<RawXml> rawXml) {

    public Relationship {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(folderId, "folderId");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(documentation, "documentation");
        Objects.requireNonNull(accessType, "accessType");
        Objects.requireNonNull(directed, "directed");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Objects.requireNonNull(rawXml, "rawXml");
        name.ifPresent(n -> Names.limited(n, "связи"));
        properties = List.copyOf(properties);
        if (!supported && rawXml.isEmpty()) {
            throw new IllegalArgumentException("FR-03: у opaque-связи " + archiId + " нет raw_xml");
        }
        if (source.value().equals(id.value()) || target.value().equals(id.value())) {
            throw new IllegalArgumentException("INV-MDL-004: связь " + archiId + " не может быть своим концом");
        }
    }

    public boolean touches(ConceptRef concept) {
        return source.equals(concept) || target.equals(concept);
    }

    public Relationship edited(Optional<String> newName, Optional<String> newDocumentation,
                               List<PropertyEntry> newProperties) {
        return new Relationship(id, folderId, archiId, archiType, source, target, newName, newDocumentation,
                accessType, directed, newProperties, sortOrder, supported, rawXml);
    }

    public Relationship movedTo(FolderId folder, SortOrder order) {
        return new Relationship(id, folder, archiId, archiType, source, target, name, documentation, accessType,
                directed, properties, order, supported, rawXml);
    }
}
