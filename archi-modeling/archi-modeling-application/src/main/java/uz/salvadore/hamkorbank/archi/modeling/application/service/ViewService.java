package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewEndpoints;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel.Created;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.StyleOverride;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutGraph;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutProposal;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutStrategy;

/** Представления и размещение на них элементов и связей. */
public final class ViewService {

    /** Поле запроса по имени в JSON — в сообщении о том, чего не хватает. */
    private static final String ELEMENT_ID = "elementId";

    private final ModelingKernel kernel;

    public ViewService(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /**
     * Создание представления с идентификаторами от сервера; без папки ({@code null}) — в корень
     * представлений; {@code null} в ключе — без идемпотентности.
     */
    public View create(EditorIdentity actor, ModelId modelId, String name, FolderId folderId,
                       String idempotencyKey) {
        return create(actor, modelId, name, folderId, RequestedIds.NONE, idempotencyKey);
    }

    /** То же с идентификаторами, которые выбрал клиент; занятые — {@code 409}. */
    public View create(EditorIdentity actor, ModelId modelId, String name, FolderId folderId,
                       RequestedIds ids, String idempotencyKey) {
        return kernel.run(Operation.CREATE_VIEW, actor, () -> kernel.unitOfWork.write(() ->
                kernel.idempotent("CreateView", actor, idempotencyKey,
                        IdempotentCommand.fingerprint(modelId, name, folderId, ids.id(), ids.archiId()),
                        () -> {
                            ArchitectureModel model = kernel.writableModel(modelId, actor);
                            FolderId folder = folderId != null ? folderId : model.roots().get(FolderType.DIAGRAMS).id();
                            ViewRef place = model.placeNewView(ViewId.of(kernel.newId(ids)), DiagramType.DIAGRAM_MODEL,
                                    name, folder, kernel.newArchiId(model, ids), kernel.now());
                            kernel.save(model);
                            View view = View.create(modelId, place);
                            kernel.views.save(view);
                            return view.id().toString();
                        },
                        ref -> kernel.views.load(ViewId.of(ref)).orElseThrow())));
    }

    /** Разместить элемент; уже размещённый не дублируется. {@code null} в родителе — корень представления. */
    public ViewNode place(EditorIdentity actor, ViewId viewId, ElementId elementId, Bounds bounds,
                          ViewNodeId parentId) {
        return place(actor, viewId, elementId, bounds, parentId, RequestedIds.NONE).value();
    }

    /**
     * То же с идентификаторами, которые выбрал клиент. Уже размещённый элемент
     * возвращает существующий узел: заданные идентификаторы тогда не проверяются и не применяются.
     */
    public Created<ViewNode> place(EditorIdentity actor, ViewId viewId, ElementId elementId, Bounds bounds,
                                   ViewNodeId parentId, RequestedIds ids) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> placeElement(actor, viewId, elementId, bounds,
                parentId, ids));
    }

    private Created<ViewNode> placeElement(EditorIdentity actor, ViewId viewId, ElementId elementId, Bounds bounds,
                                           ViewNodeId parentId, RequestedIds ids) {
        return kernel.unitOfWork.write(() -> {
            View view = loadView(viewId);
            ArchitectureModel model = kernel.writableModel(view.modelId(), actor);
            Created<ViewNode> node = view.placeElement(model, elementId, bounds, parentId,
                    () -> ViewNodeId.of(kernel.newId(ids)), () -> kernel.newArchiId(model, view, ids));
            kernel.views.save(view);
            return node;
        });
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
            Created<ViewEdge> edge = view.connect(model, relationshipId, ViewEndpoints.of(view, sourceId),
                    ViewEndpoints.of(view, targetId), bendpoints, () -> ViewEdgeId.of(kernel.newId(ids)),
                    () -> kernel.newArchiId(model, view, ids));
            kernel.views.save(view);
            return edge;
        }));
    }

    /**
     * Что поставить на представление: элемент, группу или заметку. Незаданный размер —
     * размер Archi для вида узла.
     *
     * @param elementId обязателен у узла элемента, запрещён у группы и заметки; {@code null} — не задан
     * @param label     подпись группы; {@code null} — не задана
     * @param content   текст заметки; {@code null} — не задан
     * @param width     ширина; {@code null} — по умолчанию для вида узла
     * @param height    высота; {@code null} — по умолчанию для вида узла
     * @param parentId  родитель; {@code null} — корень представления
     */
    public record NewNode(ViewNodeKind kind, ElementId elementId, String label,
                          String content, int x, int y, Integer width, Integer height,
                          ViewNodeId parentId, RequestedIds ids) {
    }

    /**
     * Узел любого вида. Узел элемента — как {@link #place}: уже размещённый элемент
     * возвращает существующий узел. Группа и заметка создаются всегда.
     */
    public Created<ViewNode> placeNode(EditorIdentity actor, ViewId viewId, NewNode request) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> {
            Bounds bounds = bounds(request);
            if (request.kind() == ViewNodeKind.DIAGRAM_OBJECT) {
                if (request.elementId() == null) {
                    throw ModelingDomainException.invalid(Message.of(ModelingMessages.FIELD_MISSING, ELEMENT_ID));
                }
                return placeElement(actor, viewId, request.elementId(), bounds, request.parentId(), request.ids());
            }
            if (request.elementId() != null) {
                throw ModelingDomainException.invalid(Message.of(ModelingMessages.FIELD_NOT_FOR_KIND, ELEMENT_ID,
                        request.kind()));
            }
            return kernel.unitOfWork.write(() -> {
                View view = loadView(viewId);
                ArchitectureModel model = kernel.writableModel(view.modelId(), actor);
                ViewNode node = view.placeAnnotation(request.kind(), request.label(), request.content(), bounds,
                        request.parentId(), ViewNodeId.of(kernel.newId(request.ids())),
                        kernel.newArchiId(model, view, request.ids()));
                kernel.views.save(view);
                return new Created<>(node, true);
            });
        });
    }

    private static Bounds bounds(NewNode request) {
        return Bounds.ofNew(request.kind(), request.x(), request.y(), request.width(), request.height());
    }

    /**
     * Вложить узел в контейнер или вынести в корень ({@code null}); координаты — относительно
     * нового родителя.
     */
    public ViewNode moveToParent(EditorIdentity actor, ViewNodeId nodeId, ViewNodeId parentId, int x,
                                 int y) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = viewOf(nodeId);
            kernel.writableModel(view.modelId(), actor);
            ViewNode node = view.moveToParent(nodeId, parentId, x, y);
            kernel.views.save(view);
            return node;
        }));
    }

    /** Подпись группы, текст заметки, собственный стиль узла; {@code null} — не меняется. */
    public ViewNode editNode(EditorIdentity actor, ViewNodeId nodeId, String label,
                             String content, StyleOverride style) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = viewOf(nodeId);
            kernel.writableModel(view.modelId(), actor);
            ViewNode node = view.editNode(nodeId, label, content, style);
            kernel.views.save(view);
            return node;
        }));
    }

    /** Собственный стиль ребра; заменяется целиком. */
    public ViewEdge editEdge(EditorIdentity actor, ViewEdgeId edgeId, StyleOverride style) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = viewOf(edgeId);
            kernel.writableModel(view.modelId(), actor);
            ViewEdge edge = view.editEdge(edgeId, style);
            kernel.views.save(view);
            return edge;
        }));
    }

    /** Убрать ребро с представления; связь остаётся в модели. */
    public void removeEdge(EditorIdentity actor, ViewEdgeId edgeId) {
        kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = viewOf(edgeId);
            kernel.writableModel(view.modelId(), actor);
            view.removeEdge(edgeId);
            kernel.views.save(view);
            return null;
        }));
    }

    /**
     * Предложение авторазметки по графу из браузера; база не меняется. Стратегия —
     * имя из контракта, по умолчанию ({@code null}) раскладка по слоям.
     */
    public LayoutProposal proposeLayout(EditorIdentity actor, ViewId viewId, String strategyName,
                                        LayoutGraph graph) {
        return kernel.run(Operation.AUTO_LAYOUT_VIEW, actor, () -> {
            LayoutStrategy strategy = strategyName == null ? LayoutStrategy.LAYERED
                    : LayoutStrategy.named(strategyName).orElseThrow(() -> ModelingDomainException.invalid(
                            Message.of(ModelingMessages.LAYOUT_STRATEGY_REQUESTED, strategyName)));
            return kernel.unitOfWork.read(() -> {
                View view = loadView(viewId);
                kernel.visibleHeader(view.modelId(), actor, AclAccess.WRITE);
                return view.proposeLayout(graph, strategy);
            });
        });
    }

    private View viewOf(ViewNodeId nodeId) {
        return loadView(kernel.views.viewOfNode(nodeId)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW_NODE, nodeId))));
    }

    private View viewOf(ViewEdgeId edgeId) {
        return loadView(kernel.views.viewOfEdge(edgeId)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW_EDGE, edgeId))));
    }

    /** Убрать узел с представления; элемент остаётся в модели. */
    public void removeNode(EditorIdentity actor, ViewNodeId nodeId) {
        kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = viewOf(nodeId);
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
        return kernel.views.load(viewId)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW, viewId)));
    }
}
