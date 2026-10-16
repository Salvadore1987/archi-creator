package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.TrackedEntities;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.TrackedMap;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.XmlText;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel.Created;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ConceptRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Names;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.AutoLayout;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutGraph;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutProposal;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutStrategy;

/**
 * Представление — отдельный агрегат: открывается
 * и сохраняется независимо от модели, а на её элементы ссылается по ключу.
 *
 * <p>Узел {@code DIAGRAM_OBJECT} ссылается на элемент той же модели, у группы
 * и заметки элемента нет; ребро соединяет узлы или рёбра этого же представления, а его связь
 * принадлежит той же модели. Геометрия — относительно родителя.
 */
public final class View extends AggregateRoot<ViewId> {

    private final ModelId modelId;
    private FolderId folderId;
    private final ArchiId archiId;
    private final DiagramType archiType;
    private String name;
    private String documentation;
    private String viewpoint;
    private List<PropertyEntry> properties;
    private SortOrder sortOrder;
    private final RawXml rawXml;
    private final long version;

    private final TrackedMap<ViewNodeId, ViewNode> nodes = new TrackedMap<>();
    private final TrackedMap<ViewEdgeId, ViewEdge> edges = new TrackedMap<>();

    private final boolean fresh;
    private boolean headerChanged;

    private View(ViewHeader header, boolean fresh) {
        super(header.id());
        this.modelId = header.modelId();
        this.folderId = header.folderId();
        this.archiId = header.archiId();
        this.archiType = header.archiType();
        this.name = header.name();
        this.documentation = header.documentation();
        this.viewpoint = header.viewpoint();
        this.properties = header.properties();
        this.sortOrder = header.sortOrder();
        this.rawXml = header.rawXml();
        this.version = header.version();
        this.fresh = fresh;
    }

    /** Пустая диаграмма ArchiMate на месте, которое ей отвела модель. */
    public static View create(ModelId modelId, ViewRef place) {
        return new View(new ViewHeader(place.id(), modelId, place.folderId(), place.archiId(), place.archiType(),
                place.name(), null, null, List.of(), place.sortOrder(), null, 0),
                true);
    }

    /** Представление из импортированного документа; ссылки проверяются по модели. */
    public static View imported(ViewHeader header, Collection<ViewNode> nodes, Collection<ViewEdge> edges,
                                ArchitectureModel model) {
        View view = new View(header, true);
        nodes.forEach(n -> view.nodes.put(n.id(), n));
        edges.forEach(e -> view.edges.put(e.id(), e));
        view.verifyIntegrity(model);
        return view;
    }

    /** Загрузка из хранилища: изменением не считается. */
    public static View restore(ViewHeader header, Collection<ViewNode> nodes, Collection<ViewEdge> edges) {
        View view = new View(header, false);
        nodes.forEach(n -> view.nodes.load(n.id(), n));
        edges.forEach(e -> view.edges.load(e.id(), e));
        return view;
    }

    private void verifyIntegrity(ArchitectureModel model) {
        if (!model.id().equals(modelId)) {
            throw integrity(Message.of(ModelingMessages.VIEW_OF_OTHER_MODEL, archiId));
        }
        Set<ArchiId> seen = new HashSet<>();
        seen.add(archiId);
        for (ViewNode node : nodes.values()) {
            if (!seen.add(node.archiId())) {
                throw new ModelingDomainException(ModelingCodes.ARCHI_ID_UNIQUE, Failure.UNPROCESSABLE,
                        Message.of(ModelingMessages.ARCHI_ID_REPEATED_ON_VIEW, node.archiId(), archiId));
            }
            if (node.elementId() != null) {
                requireElementOf(model, node.elementId(), node.archiId());
            }
            if (node.parentId() != null && !nodes.contains(node.parentId())) {
                throw integrity(Message.of(ModelingMessages.NODE_PARENT_ELSEWHERE, node.archiId()));
            }
            requireAcyclic(node);
        }
        for (ViewEdge edge : edges.values()) {
            if (!seen.add(edge.archiId())) {
                throw new ModelingDomainException(ModelingCodes.ARCHI_ID_UNIQUE, Failure.UNPROCESSABLE,
                        Message.of(ModelingMessages.ARCHI_ID_REPEATED_ON_VIEW, edge.archiId(), archiId));
            }
            requireEndpoint(edge.source(), edge.archiId());
            requireEndpoint(edge.target(), edge.archiId());
            if (edge.relationshipId() != null && !model.relationships().contains(edge.relationshipId())) {
                throw integrity(Message.of(ModelingMessages.EDGE_RELATIONSHIP_ELSEWHERE, edge.archiId()));
            }
        }
    }

    // ── Размещение ──────────────────────────────────────────────────

    /**
     * Узел над элементом модели. Уже размещённый элемент не дублируется — возвращается
     * существующий узел. {@code null} в родителе — корень представления.
     */
    public Created<ViewNode> placeElement(ArchitectureModel model, ElementId elementId, Bounds bounds,
                                          ViewNodeId parentId, ViewNodeId newId, ArchiId newArchiId) {
        requireEditable();
        requireElementOf(model, elementId, newArchiId);
        Optional<ViewNode> existing = nodes.values().stream()
                .filter(n -> elementId.equals(n.elementId())).findFirst();
        if (existing.isPresent()) {
            return new Created<>(existing.get(), false);
        }
        if (parentId != null) {
            requireContainer(parentId);
        }
        requireFreeArchiId(newArchiId);
        ViewNode node = new ViewNode(newId, parentId, newArchiId, DiagramType.DIAGRAM_OBJECT, elementId,
                bounds, StyleOverride.NONE, nextNodeOrderIn(parentId), null);
        nodes.put(node.id(), node);
        return new Created<>(node, true);
    }

    /**
     * Группа или заметка — узел без элемента модели. Подпись бывает у группы, текст —
     * у заметки. {@code null} — подписи, текста, родителя нет.
     */
    public ViewNode placeAnnotation(ViewNodeKind kind, String label, String content,
                                    Bounds bounds, ViewNodeId parentId, ViewNodeId newId,
                                    ArchiId newArchiId) {
        XmlText.require(label, Message.of(ModelingMessages.FIELD_NODE_LABEL));
        XmlText.require(content, Message.of(ModelingMessages.FIELD_NODE_CONTENT));
        requireEditable();
        if (kind != ViewNodeKind.GROUP && kind != ViewNodeKind.NOTE) {
            throw new ModelingDomainException(ModelingCodes.INVALID_INPUT, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.NODE_NOT_ANNOTATION, kind));
        }
        if (parentId != null) {
            requireContainer(parentId);
        }
        requireFreeArchiId(newArchiId);
        ViewNode node = new ViewNode(newId, parentId, newArchiId, kind.diagramType().orElseThrow(), null,
                bounds, StyleOverride.NONE, null, null,
                nextNodeOrderIn(parentId), null);
        requireTextFits(node, label, content);
        node = node.withText(label, content);
        nodes.put(node.id(), node);
        return node;
    }

    /**
     * Подпись группы, текст заметки, собственный стиль узла. {@code null} — не меняется;
     * пустая строка снимает текст; стиль заменяется целиком.
     */
    public ViewNode editNode(ViewNodeId nodeId, String label, String content, StyleOverride style) {
        XmlText.require(label, Message.of(ModelingMessages.FIELD_NODE_LABEL));
        XmlText.require(content, Message.of(ModelingMessages.FIELD_NODE_CONTENT));
        if (style != null) {
            XmlText.require(style.font(), Message.of(ModelingMessages.FIELD_FONT));
        }
        requireEditable();
        ViewNode node = requireNode(nodeId);
        requireTextFits(node, label, content);
        node = node.withText(label != null ? label : node.label(),
                content != null ? content : node.content());
        if (style != null) {
            node = node.withStyle(style);
        }
        nodes.put(nodeId, node);
        return node;
    }

    /** Подпись бывает у группы, текст — у заметки; чужое поле — отказ, а не молчаливый пропуск. */
    private static void requireTextFits(ViewNode node, String label, String content) {
        if (label != null && node.kind() != ViewNodeKind.GROUP) {
            throw notApplicable(node, "label");
        }
        if (content != null && node.kind() != ViewNodeKind.NOTE) {
            throw notApplicable(node, "content");
        }
    }

    private static ModelingDomainException notApplicable(ViewNode node, String field) {
        return new ModelingDomainException(ModelingCodes.INVALID_INPUT, Failure.UNPROCESSABLE,
                Message.of(ModelingMessages.NODE_TEXT_NOT_APPLICABLE, node.archiId(), node.kind(), field));
    }

    /** Собственный стиль ребра; заменяется целиком. */
    public ViewEdge editEdge(ViewEdgeId edgeId, StyleOverride style) {
        if (style != null) {
            XmlText.require(style.font(), Message.of(ModelingMessages.FIELD_FONT));
        }
        requireEditable();
        ViewEdge edge = requireEdge(edgeId).withStyle(style);
        edges.put(edgeId, edge);
        return edge;
    }

    /** Убрать ребро с представления: связь остаётся в модели, рёбра к этому ребру уходят с ним. */
    public void removeEdge(ViewEdgeId edgeId) {
        requireEditable();
        requireEdge(edgeId);
        removeEdgesCascading(List.of(edgeId));
    }

    /**
     * Вложить узел в контейнер или вынести в корень. Координаты — относительно нового
     * родителя; дети едут вместе с узлом, их координаты не меняются. В другом родителе
     * узел встаёт в конец содержимого, соседи не перенумеровываются. {@code null} — корень.
     */
    public ViewNode moveToParent(ViewNodeId nodeId, ViewNodeId parentId, int x, int y) {
        requireEditable();
        ViewNode node = requireNode(nodeId);
        if (parentId != null) {
            requireContainer(parentId);
            if (parentId.equals(nodeId) || isInside(parentId, nodeId)) {
                throw integrity(Message.of(ModelingMessages.NODE_NESTING_CYCLE, node.archiId()));
            }
        }
        Bounds bounds = new Bounds(x, y, node.bounds().width(), node.bounds().height());
        ViewNode moved = Objects.equals(parentId, node.parentId())
                ? node.withBounds(bounds)
                : node.withParent(parentId, bounds, nextNodeOrderIn(parentId));
        nodes.put(nodeId, moved);
        return moved;
    }

    /** Лежит ли узел где-то внутри предка. */
    private boolean isInside(ViewNodeId nodeId, ViewNodeId ancestor) {
        Set<ViewNodeId> visited = new HashSet<>();
        ViewNodeId current = parentOf(nodeId);
        while (current != null && visited.add(current)) {
            if (current.equals(ancestor)) {
                return true;
            }
            current = parentOf(current);
        }
        return false;
    }

    /** Убрать узел с представления: элемент остаётся в модели, вложенные узлы и рёбра уходят с ним. */
    public void removeNode(ViewNodeId nodeId) {
        requireEditable();
        requireNode(nodeId);
        removeNodesCascading(List.of(nodeId));
    }

    /**
     * Новая геометрия узлов и точек перегиба после перемещений. Чужой узел или ребро
     * отклоняют разметку целиком — до того, как изменится хоть один.
     */
    public void applyLayout(Map<ViewNodeId, Bounds> nodeBounds, Map<ViewEdgeId, List<Bendpoint>> edgeBendpoints) {
        requireEditable();
        nodeBounds.keySet().forEach(this::requireNode);
        edgeBendpoints.keySet().forEach(this::requireEdge);
        nodeBounds.forEach((nodeId, bounds) -> nodes.put(nodeId, requireNode(nodeId).withBounds(bounds)));
        edgeBendpoints.forEach((edgeId, points) -> edges.put(edgeId, requireEdge(edgeId).withBendpoints(points)));
    }

    /**
     * Ребро для связи модели между её отрисовками на этом представлении.
     * Концы ребра обязаны изображать концы связи.
     */
    public Created<ViewEdge> connect(ArchitectureModel model, RelationshipId relationshipId, ViewEndpoint source,
                                     ViewEndpoint target, ViewEdgeId newId, ArchiId newArchiId) {
        return connect(model, relationshipId, source, target, List.of(), newId, newArchiId);
    }

    /**
     * То же с точками перегиба нового ребра. Ребро встаёт в конец содержимого источника;
     * такое же ребро между теми же концами уже есть — возвращается оно, точки не меняются.
     */
    public Created<ViewEdge> connect(ArchitectureModel model, RelationshipId relationshipId, ViewEndpoint source,
                                     ViewEndpoint target, List<Bendpoint> bendpoints, ViewEdgeId newId,
                                     ArchiId newArchiId) {
        requireEditable();
        Relationship relationship = model.requireRelationship(relationshipId);
        requireEndpoint(source, newArchiId);
        requireEndpoint(target, newArchiId);
        if (!depicts(source, relationship.source()) || !depicts(target, relationship.target())) {
            throw integrity(Message.of(ModelingMessages.EDGE_ENDS_MISMATCH, relationship.archiId()));
        }
        Optional<ViewEdge> existing = edges.values().stream()
                .filter(e -> relationshipId.equals(e.relationshipId())
                        && e.source().equals(source) && e.target().equals(target))
                .findFirst();
        if (existing.isPresent()) {
            return new Created<>(existing.get(), false);
        }
        requireFreeArchiId(newArchiId);
        ViewEdge edge = new ViewEdge(newId, newArchiId, DiagramType.CONNECTION, relationshipId, source,
                target, bendpoints, StyleOverride.NONE, nextOrderIn(source), null);
        edges.put(edge.id(), edge);
        return new Created<>(edge, true);
    }

    /** Элемент удалён из модели: его размещения уходят вместе с ним, как в Archi. */
    public boolean removeElementReferences(ElementId elementId) {
        List<ViewNodeId> placed = nodes.values().stream()
                .filter(n -> elementId.equals(n.elementId())).map(ViewNode::id).toList();
        removeNodesCascading(placed);
        return !placed.isEmpty();
    }

    /** Связь удалена из модели: её отрисовки уходят вместе с ней. */
    public boolean removeRelationshipReferences(RelationshipId relationshipId) {
        List<ViewEdgeId> drawn = edges.values().stream()
                .filter(e -> relationshipId.equals(e.relationshipId())).map(ViewEdge::id).toList();
        removeEdgesCascading(drawn);
        return !drawn.isEmpty();
    }

    private void removeNodesCascading(List<ViewNodeId> roots) {
        Deque<ViewNodeId> queue = new ArrayDeque<>(roots);
        Set<ViewNodeId> removed = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            ViewNodeId current = queue.pop();
            if (removed.add(current)) {
                nodes.values().stream().filter(n -> current.equals(n.parentId()))
                        .forEach(child -> queue.push(child.id()));
            }
        }
        removed.forEach(nodes::remove);
        removeEdgesCascading(edges.values().stream()
                .filter(e -> removed.stream().anyMatch(e::touches)).map(ViewEdge::id).toList());
    }

    private void removeEdgesCascading(List<ViewEdgeId> roots) {
        Deque<ViewEdgeId> queue = new ArrayDeque<>(roots);
        Set<ViewEdgeId> removed = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            ViewEdgeId current = queue.pop();
            if (removed.add(current)) {
                edges.values().stream().filter(e -> e.touches(current)).forEach(e -> queue.push(e.id()));
            }
        }
        removed.forEach(edges::remove);
    }

    /**
     * Предложение авторазметки для графа представления в том виде, в каком он у автора
     * правки, с несохранёнными узлами. Представление не меняется.
     */
    public LayoutProposal proposeLayout(LayoutGraph graph, LayoutStrategy strategy) {
        requireEditable();
        return AutoLayout.propose(graph, strategy);
    }

    // ── Собственные поля ────────────────────────────────────────────

    /** Новое имя представления. */
    public void rename(String newName) {
        XmlText.require(newName, Message.of(ModelingMessages.FIELD_NAME));
        name = Names.limited(newName, Names.VIEW);
        headerChanged = true;
    }

    /**
     * Правка собственных полей: имени, документации, точки зрения и свойств;
     * {@code null} и пустые строки — значения нет.
     */
    public void edit(String newName, String newDocumentation, String newViewpoint,
                     List<PropertyEntry> newProperties) {
        XmlText.require(newName, Message.of(ModelingMessages.FIELD_NAME));
        XmlText.require(newDocumentation, Message.of(ModelingMessages.FIELD_DOCUMENTATION));
        XmlText.require(newViewpoint, Message.of(ModelingMessages.FIELD_VIEWPOINT));
        XmlText.require(newProperties);
        name = Names.limited(newName, Names.VIEW);
        documentation = newDocumentation == null || newDocumentation.isEmpty() ? null : newDocumentation;
        viewpoint = newViewpoint == null || newViewpoint.isEmpty() ? null : newViewpoint;
        properties = List.copyOf(newProperties);
        headerChanged = true;
    }

    /** Перенос в новое место дерева модели: папка и позиция берутся из места. */
    public void moveTo(ViewRef place) {
        folderId = place.folderId();
        sortOrder = place.sortOrder();
        headerChanged = true;
    }

    // ── Проверки ────────────────────────────────────────────────────

    private void requireEditable() {
        if (!archiType.equals(DiagramType.DIAGRAM_MODEL)) {
            throw new ModelingDomainException(ModelingCodes.TYPE_NOT_EDITABLE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.VIEW_NOT_EDITABLE, archiType));
        }
    }

    private void requireElementOf(ArchitectureModel model, ElementId elementId, ArchiId nodeArchiId) {
        if (!model.id().equals(modelId) || !model.elements().contains(elementId)) {
            throw integrity(Message.of(ModelingMessages.NODE_ELEMENT_ELSEWHERE, nodeArchiId));
        }
    }

    private void requireEndpoint(ViewEndpoint endpoint, ArchiId edgeArchiId) {
        boolean present = switch (endpoint) {
            case ViewNodeId node -> nodes.contains(node);
            case ViewEdgeId edge -> edges.contains(edge);
        };
        if (!present) {
            throw integrity(Message.of(ModelingMessages.EDGE_END_ELSEWHERE, edgeArchiId));
        }
    }

    /** Изображает ли конец ребра данный концепт модели. */
    private boolean depicts(ViewEndpoint endpoint, ConceptRef concept) {
        return switch (endpoint) {
            case ViewNodeId node -> concept instanceof ElementId element
                    && element.equals(nodes.get(node).map(ViewNode::elementId).orElse(null));
            case ViewEdgeId edge -> concept instanceof RelationshipId relationship
                    && relationship.equals(edges.get(edge).map(ViewEdge::relationshipId).orElse(null));
        };
    }

    /** Узел, способный содержать другие: элемент или группа, но не заметка. */
    private ViewNode requireContainer(ViewNodeId nodeId) {
        ViewNode container = requireNode(nodeId);
        if (container.kind() != ViewNodeKind.DIAGRAM_OBJECT && container.kind() != ViewNodeKind.GROUP) {
            throw integrity(Message.of(ModelingMessages.NODE_NOT_CONTAINER, container.archiId(), container.kind()));
        }
        return container;
    }

    /** Узел этого представления; нет такого — «не найдено». */
    public ViewNode requireNode(ViewNodeId nodeId) {
        return nodes.get(nodeId)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW_NODE, nodeId)));
    }

    /** Ребро этого представления; нет такого — «не найдено». */
    public ViewEdge requireEdge(ViewEdgeId edgeId) {
        return edges.get(edgeId)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW_EDGE, edgeId)));
    }

    private void requireFreeArchiId(ArchiId candidate) {
        if (archiIdTaken(candidate)) {
            throw new ModelingDomainException(ModelingCodes.ARCHI_ID_UNIQUE, Failure.CONFLICT,
                    Message.of(ModelingMessages.ARCHI_ID_TAKEN_ON_VIEW, candidate, archiId));
        }
    }

    /** Занят ли {@code archiId} самим представлением, его узлом или ребром. */
    public boolean archiIdTaken(ArchiId candidate) {
        return candidate.equals(archiId)
                || nodes.values().stream().anyMatch(n -> n.archiId().equals(candidate))
                || edges.values().stream().anyMatch(e -> e.archiId().equals(candidate));
    }

    /** Цепочка родителей узла не замыкается в цикл; цикл — нарушение целостности. */
    private void requireAcyclic(ViewNode node) {
        Set<ViewNodeId> visited = new HashSet<>();
        ViewNodeId current = node.parentId();
        while (current != null) {
            if (!visited.add(current)) {
                throw integrity(Message.of(ModelingMessages.NODE_NESTING_CYCLE, node.archiId()));
            }
            current = parentOf(current);
        }
    }

    /** Родитель узла; узла нет или он в корне — {@code null}. */
    private ViewNodeId parentOf(ViewNodeId nodeId) {
        return nodes.get(nodeId).map(ViewNode::parentId).orElse(null);
    }

    /** Конец содержимого для нового узла: верхнего уровня ({@code null}) или внутри узла-родителя. */
    private SortOrder nextNodeOrderIn(ViewNodeId parent) {
        return parent != null ? nextOrderIn(parent) : SortOrder.afterLast(childNodeOrders(null));
    }

    /**
     * Конец содержимого родителя: дети-узлы и исходящие рёбра делят одну нумерацию,
     * потому что в файле и те и другие лежат внутри родителя.
     */
    private SortOrder nextOrderIn(ViewEndpoint parent) {
        List<SortOrder> taken = new ArrayList<>();
        if (parent instanceof ViewNodeId node) {
            taken.addAll(childNodeOrders(node));
        }
        edges.values().stream().filter(e -> e.source().equals(parent)).forEach(e -> taken.add(e.sortOrder()));
        return SortOrder.afterLast(taken);
    }

    private List<SortOrder> childNodeOrders(ViewNodeId parent) {
        return nodes.values().stream().filter(n -> Objects.equals(n.parentId(), parent)).map(ViewNode::sortOrder)
                .toList();
    }

    private static ModelingDomainException integrity(Message reason) {
        return new ModelingDomainException(ModelingCodes.VIEW_REFERENCE, Failure.UNPROCESSABLE, reason);
    }

    // ── Состояние ───────────────────────────────────────────────────

    /** Собственные поля представления для записи в хранилище. */
    public ViewHeader header() {
        return new ViewHeader(id(), modelId, folderId, archiId, archiType, name, documentation, viewpoint, properties,
                sortOrder, rawXml, version);
    }


    /** Модель, которой принадлежит представление. */
    public ModelId modelId() {
        return modelId;
    }

    /** Идентификатор представления в файле Archi. */
    public ArchiId archiId() {
        return archiId;
    }

    /** Текущее имя представления. */
    public String name() {
        return name;
    }

    /** Версия строки представления для оптимистичной блокировки. */
    public long version() {
        return version;
    }

    /** Ещё не записано в хранилище: создано или импортировано в этой транзакции. */
    public boolean fresh() {
        return fresh;
    }

    /** Нужно ли записать заголовок: представление новое или его поля менялись. */
    public boolean headerChanged() {
        return fresh || headerChanged;
    }

    /** Узлы с учётом изменений. */
    public TrackedEntities<ViewNodeId, ViewNode> nodes() {
        return nodes;
    }

    /** Рёбра с учётом изменений. */
    public TrackedEntities<ViewEdgeId, ViewEdge> edges() {
        return edges;
    }

    /** Есть ли что записать: заголовок, узлы или рёбра. */
    public boolean changed() {
        return headerChanged() || nodes.changed() || edges.changed();
    }

    /** Хранилище записало узлы и рёбра: всё текущее теперь сохранено. */
    public void markPersisted() {
        nodes.markPersisted();
        edges.markPersisted();
    }
}
