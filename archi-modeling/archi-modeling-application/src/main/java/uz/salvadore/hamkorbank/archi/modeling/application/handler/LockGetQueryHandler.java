package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetLockQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LockResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.LockDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.LockStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Действующая блокировка модели: истёкшая — всё равно что нет. */
public final class LockGetQueryHandler {

    private final ModelingKernel kernel;

    public LockGetQueryHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Блокировка, которая держится сейчас; нет такой — пусто. */
    public Optional<LockResponse> handle(EditorIdentity actor, GetLockQuery query) {
        ModelId modelId = ModelId.of(query.modelId());
        return kernel.run(Operation.OPEN_MODEL, actor, () -> kernel.unitOfWork().read(() -> {
            kernel.visibleHeader(modelId, actor, AclAccess.READ);
            return kernel.locks().find(modelId).filter(l -> l.status(kernel.now()) == LockStatus.HELD)
                    .map(LockDataMapper::response);
        }));
    }
}
