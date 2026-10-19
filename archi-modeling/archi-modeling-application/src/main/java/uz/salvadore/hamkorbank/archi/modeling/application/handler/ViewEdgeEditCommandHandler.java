package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ViewDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;

/** Собственный стиль ребра заменяется целиком; не задан — собственного стиля нет. */
public final class ViewEdgeEditCommandHandler {

    private final ModelingKernel kernel;
    private final ViewLookup lookup;

    public ViewEdgeEditCommandHandler(ModelingKernel kernel, ViewLookup lookup) {
        this.kernel = kernel;
        this.lookup = lookup;
    }

    /** Ребро после правки. */
    public ViewEdgeResponse handle(EditorIdentity actor, EditViewEdgeCommand command) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork().write(() -> {
            ViewEdgeId edgeId = ViewEdgeId.of(command.edgeId());
            View view = lookup.ofEdge(edgeId);
            kernel.writableModel(view.modelId(), actor);
            ViewEdge edge = view.editEdge(edgeId, ViewDataMapper.style(command.style()));
            kernel.views().save(view);
            return ViewDataMapper.edge(edge);
        }));
    }
}
