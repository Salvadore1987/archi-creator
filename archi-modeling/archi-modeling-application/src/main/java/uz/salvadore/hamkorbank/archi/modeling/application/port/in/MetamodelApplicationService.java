package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.SuggestRelationTypesQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationTypeResponse;

/** Метамодель для палитры: каталог типов и допустимые связи. */
public interface MetamodelApplicationService {

    /** Типы элементов и соединителей с отметкой, правятся ли они. */
    List<ElementTypeResponse> listElementTypes();

    /** Допустимые связи для пары типов в порядке предложения. */
    List<RelationTypeResponse> suggestRelationTypes(SuggestRelationTypesQuery query);
}
