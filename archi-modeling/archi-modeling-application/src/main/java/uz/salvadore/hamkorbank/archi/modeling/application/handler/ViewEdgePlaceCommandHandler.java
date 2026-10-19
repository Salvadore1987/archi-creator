package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlaceViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ViewDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel.Created;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;

/**
 * Ребро уже существующей связи: концы — узлы или рёбра этого представления, изображающие
 * концы связи. Ребро встаёт в конец содержимого источника; такое же ребро между теми же
 * концами уже есть — возвращается оно.
 */
public final class ViewEdgePlaceCommandHandler {

    private final ModelingKernel kernel;
    private final ViewLookup lookup;

    public ViewEdgePlaceCommandHandler(ModelingKernel kernel, ViewLookup lookup) {
        this.kernel = kernel;
        this.lookup = lookup;
    }

    /** Новое ребро или уже стоящее между теми же концами. */
    public PlaceViewEdgeResponse handle(EditorIdentity actor, PlaceViewEdgeCommand command) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork().write(() -> {
            RelationshipId relationshipId = RelationshipId.of(command.relationshipId());
            RequestedIds ids = RequestedIds.of(command.id(), command.archiId());
            View view = lookup.view(command.viewId());
            ArchitectureModel model = kernel.writableModel(view.modelId(), actor);
            model.requireRelationship(relationshipId);
            Created<ViewEdge> edge = view.connect(model, relationshipId, ViewEndpoints.of(view, command.sourceId()),
                    ViewEndpoints.of(view, command.targetId()), ViewDataMapper.bendpoints(command.bendpoints()),
                    () -> ViewEdgeId.of(kernel.newId(ids)), () -> kernel.newArchiId(model, view, ids));
            kernel.views().save(view);
            return new PlaceViewEdgeResponse(ViewDataMapper.edge(edge.value()), edge.created());
        }));
    }
}
