package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PurgeModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Физическое уничтожение — только из {@code DELETED} и только {@code ADMIN}. */
public final class ModelPurgeCommandHandler {

    private final ModelingKernel kernel;

    public ModelPurgeCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Модель уходит из базы вместе с представлениями и версиями. */
    public void handle(EditorIdentity actor, PurgeModelCommand command) {
        ModelId modelId = ModelId.of(command.modelId());
        kernel.run(Operation.PURGE_MODEL, actor, () -> kernel.unitOfWork().write(() -> {
            ArchitectureModel model = kernel.visibleModel(modelId, actor, AclAccess.WRITE);
            model.purge(kernel.now());
            kernel.models().purge(modelId);
            kernel.events().publish(model.pullEvents());
            return null;
        }));
    }
}
