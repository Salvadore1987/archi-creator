package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RemoveViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;

/** Убрать ребро с представления; связь остаётся в модели. */
public final class ViewEdgeRemoveCommandHandler {

    private final ModelingKernel kernel;
    private final ViewLookup lookup;

    public ViewEdgeRemoveCommandHandler(ModelingKernel kernel, ViewLookup lookup) {
        this.kernel = kernel;
        this.lookup = lookup;
    }

    /** Ребро уходит с представления. */
    public void handle(EditorIdentity actor, RemoveViewEdgeCommand command) {
        kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork().write(() -> {
            ViewEdgeId edgeId = ViewEdgeId.of(command.edgeId());
            View view = lookup.ofEdge(edgeId);
            kernel.writableModel(view.modelId(), actor);
            view.removeEdge(edgeId);
            kernel.views().save(view);
            return null;
        }));
    }
}
