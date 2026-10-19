package uz.salvadore.hamkorbank.archi.modeling.application.service;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateViewCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.MoveViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RemoveViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RemoveViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveViewLayoutCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlaceViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlaceViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewEdgeEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewEdgePlaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewEdgeRemoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewLayoutSaveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodeEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodeMoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodePlaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodeRemoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Правка представлений: делегирует в handler'ы сценариев. */
public final class ViewApplicationServiceImpl implements ViewApplicationService {

    private final ViewCreateCommandHandler create;
    private final ViewNodePlaceCommandHandler placeNode;
    private final ViewEdgePlaceCommandHandler placeEdge;
    private final ViewNodeMoveCommandHandler moveNode;
    private final ViewNodeEditCommandHandler editNode;
    private final ViewEdgeEditCommandHandler editEdge;
    private final ViewNodeRemoveCommandHandler removeNode;
    private final ViewEdgeRemoveCommandHandler removeEdge;
    private final ViewLayoutSaveCommandHandler saveLayout;

    public ViewApplicationServiceImpl(ViewCreateCommandHandler create, ViewNodePlaceCommandHandler placeNode,
                                      ViewEdgePlaceCommandHandler placeEdge, ViewNodeMoveCommandHandler moveNode,
                                      ViewNodeEditCommandHandler editNode, ViewEdgeEditCommandHandler editEdge,
                                      ViewNodeRemoveCommandHandler removeNode,
                                      ViewEdgeRemoveCommandHandler removeEdge,
                                      ViewLayoutSaveCommandHandler saveLayout) {
        this.create = create;
        this.placeNode = placeNode;
        this.placeEdge = placeEdge;
        this.moveNode = moveNode;
        this.editNode = editNode;
        this.editEdge = editEdge;
        this.removeNode = removeNode;
        this.removeEdge = removeEdge;
        this.saveLayout = saveLayout;
    }

    @Override
    public ViewSummaryResponse createView(EditorIdentity actor, CreateViewCommand command) {
        return create.handle(actor, command);
    }

    @Override
    public PlaceViewNodeResponse placeNode(EditorIdentity actor, PlaceViewNodeCommand command) {
        return placeNode.handle(actor, command);
    }

    @Override
    public PlaceViewEdgeResponse placeEdge(EditorIdentity actor, PlaceViewEdgeCommand command) {
        return placeEdge.handle(actor, command);
    }

    @Override
    public ViewNodeResponse moveNode(EditorIdentity actor, MoveViewNodeCommand command) {
        return moveNode.handle(actor, command);
    }

    @Override
    public ViewNodeResponse editNode(EditorIdentity actor, EditViewNodeCommand command) {
        return editNode.handle(actor, command);
    }

    @Override
    public ViewEdgeResponse editEdge(EditorIdentity actor, EditViewEdgeCommand command) {
        return editEdge.handle(actor, command);
    }

    @Override
    public void removeNode(EditorIdentity actor, RemoveViewNodeCommand command) {
        removeNode.handle(actor, command);
    }

    @Override
    public void removeEdge(EditorIdentity actor, RemoveViewEdgeCommand command) {
        removeEdge.handle(actor, command);
    }

    @Override
    public void saveLayout(EditorIdentity actor, SaveViewLayoutCommand command) {
        saveLayout.handle(actor, command);
    }
}
