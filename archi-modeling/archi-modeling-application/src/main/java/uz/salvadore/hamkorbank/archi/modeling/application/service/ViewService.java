package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel.Created;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEndpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Представления и размещение на них элементов и связей. */
public final class ViewService {

    private final ModelingKernel kernel;

    public ViewService(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    public View create(EditorIdentity actor, ModelId modelId, String name, Optional<FolderId> folderId,
                       Optional<String> idempotencyKey) {
        return create(actor, modelId, name, folderId, RequestedIds.NONE, idempotencyKey);
    }

    /** То же с идентификаторами, которые выбрал клиент; занятые — {@code 409}. */
    public View create(EditorIdentity actor, ModelId modelId, String name, Optional<FolderId> folderId,
                       RequestedIds ids, Optional<String> idempotencyKey) {
        return kernel.run(Operation.CREATE_VIEW, actor, () -> kernel.unitOfWork.write(() ->
                kernel.idempotent("CreateView", actor, idempotencyKey,
                        IdempotentCommand.fingerprint(modelId, name, folderId, ids.id(), ids.archiId()),
                        () -> {
                            ArchitectureModel model = kernel.writableModel(modelId, actor);
                            FolderId folder = folderId.orElseGet(() -> model.roots().get(FolderType.DIAGRAMS).id());
                            ViewRef place = model.placeNewView(ViewId.of(kernel.newId(ids)), DiagramType.DIAGRAM_MODEL,
                                    name, folder, kernel.newArchiId(model, ids), kernel.now());
                            kernel.save(model);
                            View view = View.create(modelId, place);
                            kernel.views.save(view);
                            return view.id().toString();
                        },
                        ref -> kernel.views.load(ViewId.of(ref)).orElseThrow())));
    }

    /** Разместить элемент; уже размещённый не дублируется. */
    public ViewNode place(EditorIdentity actor, ViewId viewId, ElementId elementId, Bounds bounds,
                          Optional<ViewNodeId> parentId) {
        return place(actor, viewId, elementId, bounds, parentId, RequestedIds.NONE).value();
    }

    /**
     * То же с идентификаторами, которые выбрал клиент. Уже размещённый элемент
     * возвращает существующий узел: заданные идентификаторы тогда не проверяются и не применяются.
     */
    public Created<ViewNode> place(EditorIdentity actor, ViewId viewId, ElementId elementId, Bounds bounds,
                                   Optional<ViewNodeId> parentId, RequestedIds ids) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = loadView(viewId);
            ArchitectureModel model = kernel.writableModel(view.modelId(), actor);
            Optional<ViewNode> placed = view.nodes().values().stream()
                    .filter(n -> n.elementId().equals(Optional.of(elementId))).findFirst();
            if (placed.isPresent()) {
                return new Created<>(placed.get(), false);
            }
            Created<ViewNode> node = view.placeElement(model, elementId, bounds, parentId,
                    ViewNodeId.of(kernel.newId(ids)), kernel.newArchiId(model, view, ids));
            kernel.views.save(view);
            return node;
        }));
    }

    /**
     * Ребро уже существующей связи на представлении: концы — узлы или рёбра этого представления,
     * изображающие концы связи. Ребро встаёт в конец содержимого источника. Такое же ребро
     * между теми же концами уже есть — возвращается оно.
     */
    public Created<ViewEdge> placeEdge(EditorIdentity actor, ViewId viewId, RelationshipId relationshipId,
                                       UUID sourceId, UUID targetId, List<Bendpoint> bendpoints, RequestedIds ids) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = loadView(viewId);
            ArchitectureModel model = kernel.writableModel(view.modelId(), actor);
            model.requireRelationship(relationshipId);
            ViewEndpoint source = endpoint(view, sourceId);
            ViewEndpoint target = endpoint(view, targetId);
            Optional<ViewEdge> drawn = view.edges().values().stream()
                    .filter(e -> e.relationshipId().equals(Optional.of(relationshipId))
                            && e.source().equals(source) && e.target().equals(target))
                    .findFirst();
            if (drawn.isPresent()) {
                return new Created<>(drawn.get(), false);
            }
            Created<ViewEdge> edge = view.connect(model, relationshipId, source, target, bendpoints,
                    ViewEdgeId.of(kernel.newId(ids)), kernel.newArchiId(model, view, ids));
            kernel.views.save(view);
            return edge;
        }));
    }

    /** Конец ребра по ключу: узел, если такой узел есть на представлении, иначе ребро. */
    static ViewEndpoint endpoint(View view, UUID id) {
        return view.nodes().contains(ViewNodeId.of(id)) ? ViewNodeId.of(id) : ViewEdgeId.of(id);
    }

    /** Убрать узел с представления; элемент остаётся в модели. */
    public void removeNode(EditorIdentity actor, ViewNodeId nodeId) {
        kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = loadView(kernel.views.viewOfNode(nodeId)
                    .orElseThrow(() -> ModelingException.notFound("узел представления " + nodeId)));
            kernel.writableModel(view.modelId(), actor);
            view.removeNode(nodeId);
            kernel.views.save(view);
            return null;
        }));
    }

    /** Геометрия после перемещений одной транзакцией (SaveViewLayout). */
    public View saveLayout(EditorIdentity actor, ViewId viewId, Map<ViewNodeId, Bounds> nodeBounds,
                           Map<ViewEdgeId, List<Bendpoint>> edgeBendpoints) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = loadView(viewId);
            kernel.writableModel(view.modelId(), actor);
            view.applyLayout(nodeBounds, edgeBendpoints);
            kernel.views.save(view);
            return view;
        }));
    }

    private View loadView(ViewId viewId) {
        return kernel.views.load(viewId).orElseThrow(() -> ModelingException.notFound("представление " + viewId));
    }
}
