package uz.salvadore.hamkorbank.archi.modeling.application.mapper;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptDefinition;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationshipType;

/** Каталог метамодели — в результаты запросов палитры. */
public final class MetamodelDataMapper {

    private MetamodelDataMapper() {
    }

    /** Тип элемента; фаза пуста у типа вне фаз. */
    public static ElementTypeResponse elementType(ConceptDefinition c) {
        return new ElementTypeResponse(c.type().value(), c.kind().name(), c.layer().name(),
                c.phase() == null ? null : c.phase().name(), c.supported());
    }

    /** Предложенный тип связи. */
    public static RelationTypeResponse relationType(RelationshipType type, boolean byDefault, boolean nesting) {
        return new RelationTypeResponse(type.archiType().value(), type.name(), byDefault, nesting);
    }
}
