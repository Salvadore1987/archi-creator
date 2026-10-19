package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RestoreModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Возврат мягко удалённой модели в работу; только {@code ADMIN}. */
public final class ModelRestoreCommandHandler {

    private final ModelingKernel kernel;

    public ModelRestoreCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Модель снова активна. */
    public ModelSummaryResponse handle(EditorIdentity actor, RestoreModelCommand command) {
        return kernel.run(Operation.RESTORE_MODEL, actor, () -> kernel.unitOfWork().write(() -> {
            ArchitectureModel model = kernel.visibleModel(ModelId.of(command.modelId()), actor, AclAccess.WRITE);
            model.restoreDeleted(kernel.now());
            kernel.save(model);
            return ModelDataMapper.summary(model.header());
        }));
    }
}
