package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.SuggestRelationTypesQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.MetamodelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.NestingRules;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationMatrix;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationshipType;

/** Допустимые связи для пары типов; первая — выбор по умолчанию, подразумеваемая вложением помечена. */
public final class RelationTypeSuggestQueryHandler {

    /**
     * Порядок, в котором типы предлагаются: первый допустимый — выбор по умолчанию.
     * Наиболее частые в ландшафте — впереди.
     */
    private static final List<RelationshipType> PREFERENCE = List.of(
            RelationshipType.SERVING, RelationshipType.REALIZATION, RelationshipType.ASSIGNMENT,
            RelationshipType.COMPOSITION, RelationshipType.AGGREGATION, RelationshipType.FLOW,
            RelationshipType.TRIGGERING, RelationshipType.ACCESS, RelationshipType.INFLUENCE,
            RelationshipType.SPECIALIZATION, RelationshipType.ASSOCIATION);

    /** Что предложить при рисовании связи от источника к цели; неизвестный тип — отказ во вводе. */
    public List<RelationTypeResponse> handle(SuggestRelationTypesQuery query) {
        ArchiType sourceType = RequestValues.archiType(query.sourceType());
        ArchiType targetType = RequestValues.archiType(query.targetType());
        List<RelationshipType> permitted = RelationMatrix.archimate32().permitted(sourceType, targetType).stream()
                .sorted(Comparator.comparingInt(PREFERENCE::indexOf)).toList();
        Optional<RelationshipType> nesting = NestingRules.archimate32().impliedRelationship(sourceType, targetType);
        return IntStream.range(0, permitted.size())
                .mapToObj(i -> MetamodelDataMapper.relationType(permitted.get(i), i == 0,
                        nesting.equals(Optional.of(permitted.get(i)))))
                .toList();
    }
}
