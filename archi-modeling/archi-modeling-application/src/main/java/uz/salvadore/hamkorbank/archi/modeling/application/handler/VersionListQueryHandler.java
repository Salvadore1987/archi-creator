package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListVersionsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.VersionDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** История версий модели, от новых к старым, без снимков. */
public final class VersionListQueryHandler {

    private final ModelingKernel kernel;

    public VersionListQueryHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Версии модели, видимой на чтение. */
    public List<VersionResponse> handle(EditorIdentity actor, ListVersionsQuery query) {
        return kernel.run(Operation.LIST_VERSIONS, actor, () -> kernel.unitOfWork().read(() -> {
            ModelId modelId = ModelId.of(query.modelId());
            kernel.visibleHeader(modelId, actor, AclAccess.READ);
            return kernel.versions().list(modelId).stream().map(VersionDataMapper::version).toList();
        }));
    }
}
