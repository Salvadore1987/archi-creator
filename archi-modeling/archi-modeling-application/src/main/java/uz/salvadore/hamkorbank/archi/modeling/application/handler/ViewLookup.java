package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Представление по своему ключу, по ключу узла или ребра; нет такого — «не найдено». */
public final class ViewLookup {

    private final ModelingKernel kernel;

    public ViewLookup(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Представление по ключу. */
    public View view(UUID viewId) {
        return kernel.views().load(ViewId.of(viewId)).orElseThrow(
                () -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW, ViewId.of(viewId))));
    }

    /** Представление, на котором стоит узел. */
    public View ofNode(ViewNodeId nodeId) {
        return view(kernel.views().viewOfNode(nodeId).orElseThrow(
                () -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW_NODE, nodeId))).value());
    }

    /** Представление, на котором стоит ребро. */
    public View ofEdge(ViewEdgeId edgeId) {
        return view(kernel.views().viewOfEdge(edgeId).orElseThrow(
                () -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW_EDGE, edgeId))).value());
    }
}
