package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Мягкое удаление: данные и история остаются, блокировка автора снимается. */
public final class ModelDeleteCommandHandler {

    private final ModelingKernel kernel;

    public ModelDeleteCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Удаляет модель под своей блокировкой и снимает её. */
    public void handle(EditorIdentity actor, DeleteModelCommand command) {
        ModelId modelId = ModelId.of(command.modelId());
        kernel.run(Operation.DELETE_MODEL, actor, () -> kernel.unitOfWork().write(() -> {
            ArchitectureModel model = kernel.writableModel(modelId, actor);
            model.delete(actor, kernel.now());
            kernel.save(model);
            ModelLock lock = kernel.locks().find(modelId).orElseThrow();
            kernel.locks().delete(modelId);
            kernel.events().publish(List.of(lock.release(actor, kernel.now())));
            return null;
        }));
    }
}
