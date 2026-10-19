package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.SuggestRelationTypesQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementTypeListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationTypeSuggestQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.MetamodelApplicationService;

/** Метамодель для палитры: делегирует в handler'ы запросов. */
public final class MetamodelApplicationServiceImpl implements MetamodelApplicationService {

    private final ElementTypeListQueryHandler elementTypes;
    private final RelationTypeSuggestQueryHandler relationTypes;

    public MetamodelApplicationServiceImpl(ElementTypeListQueryHandler elementTypes,
                                           RelationTypeSuggestQueryHandler relationTypes) {
        this.elementTypes = elementTypes;
        this.relationTypes = relationTypes;
    }

    @Override
    public List<ElementTypeResponse> listElementTypes() {
        return elementTypes.handle();
    }

    @Override
    public List<RelationTypeResponse> suggestRelationTypes(SuggestRelationTypesQuery query) {
        return relationTypes.handle(query);
    }
}
