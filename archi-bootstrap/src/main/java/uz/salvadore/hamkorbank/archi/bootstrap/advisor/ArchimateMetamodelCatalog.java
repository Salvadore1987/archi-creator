package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import uz.salvadore.hamkorbank.archi.advisor.application.port.MetamodelCatalog;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptDefinition;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationMatrix;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationshipType;

/**
 * Метамодель для помощника — каталог типов и матрица связей modeling, те же, по которым
 * сервер принимает и отклоняет связи. Подсказка и план не могут разойтись с сервером:
 * второй копии правил нет.
 */
public final class ArchimateMetamodelCatalog implements MetamodelCatalog {

    private final ArchiTypeRegistry registry = ArchiTypeRegistry.archimate32();
    private final RelationMatrix matrix = RelationMatrix.archimate32();
    private final List<String> elementTypes = registry.concepts(ConceptKind.ELEMENT).stream()
            .filter(ConceptDefinition::supported)
            .map(c -> c.type().value()).sorted().toList();
    private final List<String> relationshipTypes = java.util.Arrays.stream(RelationshipType.values())
            .map(r -> r.archiType().value()).sorted().toList();

    @Override
    public List<String> elementTypes() {
        return elementTypes;
    }

    @Override
    public List<String> relationshipTypes() {
        return relationshipTypes;
    }

    @Override
    public Map<String, List<String>> permittedFrom(String sourceType) {
        Optional<ArchiType> source = type(sourceType);
        if (source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> permitted = new TreeMap<>();
        for (String target : elementTypes) {
            Set<RelationshipType> relations = matrix.permitted(source.get(), ArchiType.of(target));
            relations.forEach(r -> permitted.computeIfAbsent(r.archiType().value(),
                    k -> new java.util.ArrayList<>()).add(target));
        }
        return new LinkedHashMap<>(permitted);
    }

    @Override
    public boolean permitted(String sourceType, String targetType, String relationshipType) {
        Optional<ArchiType> source = type(sourceType);
        Optional<ArchiType> target = type(targetType);
        Optional<RelationshipType> relation = type(relationshipType).flatMap(RelationshipType::fromArchiType);
        return source.isPresent() && target.isPresent() && relation.isPresent()
                && matrix.isPermitted(source.get(), target.get(), relation.get());
    }

    private Optional<ArchiType> type(String value) {
        try {
            ArchiType type = ArchiType.of(value);
            return matrix.knows(type) ? Optional.of(type) : Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
