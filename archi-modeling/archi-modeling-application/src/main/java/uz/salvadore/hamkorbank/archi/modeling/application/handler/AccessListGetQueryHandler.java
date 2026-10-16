package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetAccessListQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.AccessListResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.AccessListDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Список доступа модели — тому, кому она видна. */
public final class AccessListGetQueryHandler {

    private final ModelingKernel kernel;

    public AccessListGetQueryHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Список доступа модели. */
    public AccessListResponse handle(EditorIdentity actor, GetAccessListQuery query) {
        ModelId modelId = ModelId.of(query.modelId());
        return kernel.run(Operation.OPEN_MODEL, actor, () -> kernel.unitOfWork().read(() -> {
            kernel.visibleHeader(modelId, actor, AclAccess.READ);
            return AccessListDataMapper.response(kernel.accessLists().find(modelId));
        }));
    }
}
