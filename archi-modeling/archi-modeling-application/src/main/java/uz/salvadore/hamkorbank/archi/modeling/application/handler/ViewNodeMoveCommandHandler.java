package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.MoveViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ViewDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Вложить узел в контейнер или вынести в корень; координаты — относительно нового родителя. */
public final class ViewNodeMoveCommandHandler {

    private final ModelingKernel kernel;
    private final ViewLookup lookup;

    public ViewNodeMoveCommandHandler(ModelingKernel kernel, ViewLookup lookup) {
        this.kernel = kernel;
        this.lookup = lookup;
    }

    /** Узел на новом месте. */
    public ViewNodeResponse handle(EditorIdentity actor, MoveViewNodeCommand command) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork().write(() -> {
            ViewNodeId nodeId = ViewNodeId.of(command.nodeId());
            View view = lookup.ofNode(nodeId);
            kernel.writableModel(view.modelId(), actor);
            ViewNode node = view.moveToParent(nodeId,
                    command.parentId() == null ? null : ViewNodeId.of(command.parentId()), command.x(), command.y());
            kernel.views().save(view);
            return ViewDataMapper.node(node);
        }));
    }
}
