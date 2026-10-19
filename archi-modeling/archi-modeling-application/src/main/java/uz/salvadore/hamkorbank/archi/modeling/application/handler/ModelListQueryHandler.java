package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListModelsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ResolveWorkspaceQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.ModelAccessList;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Модели пространства, которые автор видит по списку доступа. Удалённые — только
 * администратору: им их и восстанавливать.
 */
public final class ModelListQueryHandler {

    private final ModelingKernel kernel;
    private final WorkspaceResolveQueryHandler resolve;

    public ModelListQueryHandler(ModelingKernel kernel, WorkspaceResolveQueryHandler resolve) {
        this.kernel = kernel;
        this.resolve = resolve;
    }

    /** Видимые автору модели пространства; не названо — первое. */
    public List<ModelSummaryResponse> handle(EditorIdentity actor, ListModelsQuery query) {
        return kernel.run(Operation.OPEN_MODEL, actor, () -> {
            WorkspaceId workspaceId = WorkspaceId.of(
                    resolve.handle(new ResolveWorkspaceQuery(query.workspaceId())).workspaceId());
            return kernel.unitOfWork().read(() -> {
                List<ModelHeader> headers = kernel.models().list(workspaceId);
                Map<ModelId, ModelAccessList> acl = kernel.accessLists()
                        .findAll(headers.stream().map(ModelHeader::id).toList());
                return headers.stream()
                        .filter(h -> h.status() == ModelStatus.ACTIVE || actor.isAdmin())
                        .filter(h -> acl.getOrDefault(h.id(), ModelAccessList.open(h.id()))
                                .permits(actor, AclAccess.READ))
                        .map(ModelDataMapper::summary)
                        .toList();
            });
        });
    }
}
