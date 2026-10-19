package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ViewDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Подпись группы, текст заметки, собственный стиль узла; незаданное поле не меняется. */
public final class ViewNodeEditCommandHandler {

    private final ModelingKernel kernel;
    private final ViewLookup lookup;

    public ViewNodeEditCommandHandler(ModelingKernel kernel, ViewLookup lookup) {
        this.kernel = kernel;
        this.lookup = lookup;
    }

    /** Узел после правки. */
    public ViewNodeResponse handle(EditorIdentity actor, EditViewNodeCommand command) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork().write(() -> {
            ViewNodeId nodeId = ViewNodeId.of(command.nodeId());
            View view = lookup.ofNode(nodeId);
            kernel.writableModel(view.modelId(), actor);
            ViewNode node = view.editNode(nodeId, command.label(), command.content(),
                    command.style() == null ? null : ViewDataMapper.style(command.style()));
            kernel.views().save(view);
            return ViewDataMapper.node(node);
        }));
    }
}
