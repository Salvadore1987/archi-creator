package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RemoveViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Убрать узел с представления; элемент остаётся в модели. */
public final class ViewNodeRemoveCommandHandler {

    private final ModelingKernel kernel;
    private final ViewLookup lookup;

    public ViewNodeRemoveCommandHandler(ModelingKernel kernel, ViewLookup lookup) {
        this.kernel = kernel;
        this.lookup = lookup;
    }

    /** Узел уходит вместе со своими рёбрами. */
    public void handle(EditorIdentity actor, RemoveViewNodeCommand command) {
        kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork().write(() -> {
            ViewNodeId nodeId = ViewNodeId.of(command.nodeId());
            View view = lookup.ofNode(nodeId);
            kernel.writableModel(view.modelId(), actor);
            view.removeNode(nodeId);
            kernel.views().save(view);
            return null;
        }));
    }
}
