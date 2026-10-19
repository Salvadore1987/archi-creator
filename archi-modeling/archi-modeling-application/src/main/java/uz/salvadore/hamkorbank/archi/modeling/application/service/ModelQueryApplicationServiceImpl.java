package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListModelsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ValidateModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ValidationFindingResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelValidateQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Запросы модели: делегирует в handler'ы сценариев. */
public final class ModelQueryApplicationServiceImpl implements ModelQueryApplicationService {

    private final ModelListQueryHandler list;
    private final ModelGetQueryHandler get;
    private final ModelValidateQueryHandler validate;

    public ModelQueryApplicationServiceImpl(ModelListQueryHandler list, ModelGetQueryHandler get,
                                            ModelValidateQueryHandler validate) {
        this.list = list;
        this.get = get;
        this.validate = validate;
    }

    @Override
    public List<ModelSummaryResponse> listModels(EditorIdentity actor, ListModelsQuery query) {
        return list.handle(actor, query);
    }

    @Override
    public ModelTreeResponse getModel(EditorIdentity actor, GetModelQuery query) {
        return get.handle(actor, query);
    }

    @Override
    public List<ValidationFindingResponse> validateModel(EditorIdentity actor, ValidateModelQuery query) {
        return validate.handle(actor, query);
    }
}
