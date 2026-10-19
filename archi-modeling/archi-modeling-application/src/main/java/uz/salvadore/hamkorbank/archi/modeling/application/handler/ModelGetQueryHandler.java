package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Дерево модели вместе с размещениями на представлениях (OpenModel). */
public final class ModelGetQueryHandler {

    private final ModelingKernel kernel;

    public ModelGetQueryHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Дерево видимой автору модели; размер модели уходит в метрику. */
    public ModelTreeResponse handle(EditorIdentity actor, GetModelQuery query) {
        ModelId modelId = ModelId.of(query.modelId());
        return kernel.run(Operation.OPEN_MODEL, actor, () -> kernel.unitOfWork().read(() -> {
            ArchitectureModel model = kernel.visibleModel(modelId, actor, AclAccess.READ);
            kernel.metrics().modelSize(modelId, model.elements().size());
            return ModelDataMapper.tree(model, kernel.views().placements(modelId));
        }));
    }
}
