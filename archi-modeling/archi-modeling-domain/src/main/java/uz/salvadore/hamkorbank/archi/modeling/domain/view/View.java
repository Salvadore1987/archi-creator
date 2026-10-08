package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.TrackedMap;
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

/**
 * Представление — отдельный агрегат (spec/domain/modeling/aggregates.yaml#View): открывается
 * и сохраняется независимо от модели, а на её элементы ссылается по ключу.
 *
 * <p>INV-MDL-008: узел {@code DIAGRAM_OBJECT} ссылается на элемент той же модели, у группы
 * и заметки элемента нет; ребро соединяет узлы или рёбра этого же представления, а его связь
 * принадлежит той же модели. Геометрия — относительно родителя.
 */
public final class View {

    private final ViewId id;
    private final ModelId modelId;
    private FolderId folderId;
    private final ArchiId archiId;
    private final DiagramType archiType;
    private String name;
    private Optional<String> documentation;
    private Optional<String> viewpoint;
    private List<PropertyEntry> properties;
    private SortOrder sortOrder;
    private final Optional<RawXml> rawXml;
    private final long version;

    private final TrackedMap<ViewNodeId, ViewNode> nodes = new TrackedMap<>();
    private final TrackedMap<ViewEdgeId, ViewEdge> edges = new TrackedMap<>();

    private final boolean fresh;
    private boolean headerChanged;

    private View(ViewHeader header, boolean fresh) {
        this.id = header.id();
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
                place.name(), Optional.empty(), Optional.empty(), List.of(), place.sortOrder(), Optional.empty(), 0),
                true);
    }

    /** Представление из импортированного документа; ссылки проверяются по модели (INV-MDL-008). */
    public static View imported(ViewHeader header, Collection<ViewNode> nodes, Collection<ViewEdge> edges,
                                ArchitectureModel model) {
        View view = new View(header, true);
        nodes.forEach(n -> view.nodes.put(n.id(), n));
        edges.forEach(e -> view.edges.put(e.id(), e));
        view.verifyIntegrity(model);
        return view;
    }

    public static View restore(ViewHeader header, Collection<ViewNode> nodes, Collection<ViewEdge> edges) {
        View view = new View(header, false);
        nodes.forEach(n -> view.nodes.load(n.id(), n));
        edges.forEach(e -> view.edges.load(e.id(), e));
        return view;
    }

    private void verifyIntegrity(ArchitectureModel model) {
        if (!model.id().equals(modelId)) {
            throw integrity("представление " + archiId + " принадлежит другой модели");
        }
        Set<ArchiId> seen = new HashSet<>();
        for (ViewNode node : nodes.values()) {
            if (!seen.add(node.archiId())) {
                throw new ModelingException("INV-MDL-001", Failure.UNPROCESSABLE,
                        "archi_id " + node.archiId() + " повторяется на представлении " + archiId);
            }
            node.elementId().ifPresent(element -> requireElementOf(model, element, node.archiId()));
            node.parentId().ifPresent(parent -> {
                if (!nodes.contains(parent)) {
                    throw integrity("родитель узла " + node.archiId() + " не на этом представлении");
                }
            });
            depth(node);
        }
        for (ViewEdge edge : edges.values()) {
            if (!seen.add(edge.archiId())) {
                throw new ModelingException("INV-MDL-001", Failure.UNPROCESSABLE,
                        "archi_id " + edge.archiId() + " повторяется на представлении " + archiId);
            }
            requireEndpoint(edge.source(), edge.archiId());
            requireEndpoint(edge.target(), edge.archiId());
            edge.relationshipId().ifPresent(r -> {
                if (!model.relationships().contains(r)) {
                    throw integrity("связь ребра " + edge.archiId() + " не принадлежит модели");
                }
            });
        }
    }

    // ── Размещение (UC-MDL-002) ─────────────────────────────────────

    /**
     * Узел над элементом модели. Уже размещённый элемент не дублируется — возвращается
     * существующий узел (UI-002).
     */
    public Created<ViewNode> placeElement(ArchitectureModel model, ElementId elementId, Bounds bounds,
                                          Optional<ViewNodeId> parentId, ViewNodeId newId, ArchiId newArchiId) {
        requireEditable();
        requireElementOf(model, elementId, newArchiId);
        Optional<ViewNode> existing = nodes.values().stream()
                .filter(n -> n.elementId().equals(Optional.of(elementId))).findFirst();
        if (existing.isPresent()) {
            return new Created<>(existing.get(), false);
        }
        parentId.ifPresent(this::requireNode);
        requireFreeArchiId(newArchiId);
        ViewNode node = new ViewNode(newId, parentId, newArchiId, DiagramType.DIAGRAM_OBJECT, Optional.of(elementId),
                bounds, StyleOverride.NONE, nextOrderIn(parentId.map(p -> (ViewEndpoint) p)), Optional.empty());
        nodes.put(node.id(), node);
        return new Created<>(node, true);
    }

    /** Убрать узел с представления: элемент остаётся в модели, вложенные узлы и рёбра уходят с ним. */
    public void removeNode(ViewNodeId nodeId) {
        requireEditable();
        requireNode(nodeId);
        removeNodesCascading(List.of(nodeId));
    }

    /** Новая геометрия узлов и точек перегиба после перемещений (PUT /views/{id}/layout). */
    public void applyLayout(Map<ViewNodeId, Bounds> nodeBounds, Map<ViewEdgeId, List<Bendpoint>> edgeBendpoints) {
        requireEditable();
        nodeBounds.forEach((nodeId, bounds) -> nodes.put(nodeId, requireNode(nodeId).withBounds(bounds)));
        edgeBendpoints.forEach((edgeId, points) -> edges.put(edgeId, requireEdge(edgeId).withBendpoints(points)));
    }

    /**
     * Ребро для связи модели между её отрисовками на этом представлении (UC-MDL-003, п. 5).
     * Концы ребра обязаны изображать концы связи.
     */
    public Created<ViewEdge> connect(ArchitectureModel model, RelationshipId relationshipId, ViewEndpoint source,
                                     ViewEndpoint target, ViewEdgeId newId, ArchiId newArchiId) {
        requireEditable();
        Relationship relationship = model.requireRelationship(relationshipId);
        requireEndpoint(source, newArchiId);
        requireEndpoint(target, newArchiId);
        if (!depicts(source, relationship.source()) || !depicts(target, relationship.target())) {
            throw integrity("концы ребра не изображают концы связи " + relationship.archiId());
        }
        Optional<ViewEdge> existing = edges.values().stream()
                .filter(e -> e.relationshipId().equals(Optional.of(relationshipId))
                        && e.source().equals(source) && e.target().equals(target))
                .findFirst();
        if (existing.isPresent()) {
            return new Created<>(existing.get(), false);
        }
        requireFreeArchiId(newArchiId);
        ViewEdge edge = new ViewEdge(newId, newArchiId, DiagramType.CONNECTION, Optional.of(relationshipId), source,
                target, List.of(), StyleOverride.NONE, nextOrderIn(Optional.of(source)), Optional.empty());
        edges.put(edge.id(), edge);
        return new Created<>(edge, true);
    }

    /** Элемент удалён из модели: его размещения уходят вместе с ним, как в Archi. */
    public boolean removeElementReferences(ElementId elementId) {
        List<ViewNodeId> placed = nodes.values().stream()
                .filter(n -> n.elementId().equals(Optional.of(elementId))).map(ViewNode::id).toList();
        removeNodesCascading(placed);
        return !placed.isEmpty();
    }

    /** Связь удалена из модели: её отрисовки уходят вместе с ней. */
    public boolean removeRelationshipReferences(RelationshipId relationshipId) {
        List<ViewEdgeId> drawn = edges.values().stream()
                .filter(e -> e.relationshipId().equals(Optional.of(relationshipId))).map(ViewEdge::id).toList();
        removeEdgesCascading(drawn);
        return !drawn.isEmpty();
    }

    private void removeNodesCascading(List<ViewNodeId> roots) {
        Deque<ViewNodeId> queue = new ArrayDeque<>(roots);
        Set<ViewNodeId> removed = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            ViewNodeId current = queue.pop();
            if (removed.add(current)) {
                nodes.values().stream().filter(n -> n.parentId().equals(Optional.of(current)))
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

    // ── Собственные поля ────────────────────────────────────────────

    public void rename(String newName) {
        name = Names.limited(newName, "представления");
        headerChanged = true;
    }

    public void edit(String newName, Optional<String> newDocumentation, Optional<String> newViewpoint,
                     List<PropertyEntry> newProperties) {
        name = Names.limited(newName, "представления");
        documentation = newDocumentation.filter(d -> !d.isEmpty());
        viewpoint = newViewpoint.filter(v -> !v.isEmpty());
        properties = List.copyOf(newProperties);
        headerChanged = true;
    }

    public void moveTo(ViewRef place) {
        folderId = place.folderId();
        sortOrder = place.sortOrder();
        headerChanged = true;
    }

    // ── Проверки ────────────────────────────────────────────────────

    private void requireEditable() {
        if (!archiType.equals(DiagramType.DIAGRAM_MODEL)) {
            throw new ModelingException(ModelingException.Codes.TYPE_NOT_EDITABLE, Failure.UNPROCESSABLE,
                    "представление " + archiType + " хранится как есть и не редактируется (FR-03)");
        }
    }

    private void requireElementOf(ArchitectureModel model, ElementId elementId, ArchiId nodeArchiId) {
        if (!model.id().equals(modelId) || !model.elements().contains(elementId)) {
            throw integrity("элемент узла " + nodeArchiId + " не принадлежит модели представления");
        }
    }

    private void requireEndpoint(ViewEndpoint endpoint, ArchiId edgeArchiId) {
        boolean present = switch (endpoint) {
            case ViewNodeId node -> nodes.contains(node);
            case ViewEdgeId edge -> edges.contains(edge);
        };
        if (!present) {
            throw integrity("конец ребра " + edgeArchiId + " не на этом представлении");
        }
    }

    /** Изображает ли конец ребра данный концепт модели. */
    private boolean depicts(ViewEndpoint endpoint, ConceptRef concept) {
        return switch (endpoint) {
            case ViewNodeId node -> concept instanceof ElementId element
                    && nodes.get(node).flatMap(ViewNode::elementId).equals(Optional.of(element));
            case ViewEdgeId edge -> concept instanceof RelationshipId relationship
                    && edges.get(edge).flatMap(ViewEdge::relationshipId).equals(Optional.of(relationship));
        };
    }

    public ViewNode requireNode(ViewNodeId nodeId) {
        return nodes.get(nodeId).orElseThrow(() -> ModelingException.notFound("узел представления " + nodeId));
    }

    public ViewEdge requireEdge(ViewEdgeId edgeId) {
        return edges.get(edgeId).orElseThrow(() -> ModelingException.notFound("ребро представления " + edgeId));
    }

    private void requireFreeArchiId(ArchiId candidate) {
        if (candidate.equals(archiId)
                || nodes.values().stream().anyMatch(n -> n.archiId().equals(candidate))
                || edges.values().stream().anyMatch(e -> e.archiId().equals(candidate))) {
            throw new ModelingException("INV-MDL-001", Failure.CONFLICT,
                    "archi_id " + candidate + " уже занят на представлении " + archiId);
        }
    }

    /** Глубина вложенности; цикл в родителях — нарушение целостности. */
    private int depth(ViewNode node) {
        int depth = 0;
        Set<ViewNodeId> visited = new HashSet<>();
        Optional<ViewNodeId> current = node.parentId();
        while (current.isPresent()) {
            if (!visited.add(current.get())) {
                throw integrity("цикл во вложенности узлов через " + node.archiId());
            }
            depth++;
            current = nodes.get(current.get()).flatMap(ViewNode::parentId);
        }
        return depth;
    }

    /**
     * Конец содержимого родителя: дети-узлы и исходящие рёбра делят одну нумерацию,
     * потому что в файле и те и другие лежат внутри родителя (ADR-0017).
     */
    private SortOrder nextOrderIn(Optional<ViewEndpoint> parent) {
        List<SortOrder> taken = new ArrayList<>();
        if (parent.isEmpty() || parent.get() instanceof ViewNodeId) {
            Optional<ViewNodeId> parentNode = parent.map(p -> (ViewNodeId) p);
            nodes.values().stream().filter(n -> n.parentId().equals(parentNode)).forEach(n -> taken.add(n.sortOrder()));
        }
        parent.ifPresent(p -> edges.values().stream().filter(e -> e.source().equals(p))
                .forEach(e -> taken.add(e.sortOrder())));
        return SortOrder.afterLast(taken);
    }

    private static ModelingException integrity(String message) {
        return new ModelingException("INV-MDL-008", Failure.UNPROCESSABLE, message);
    }

    // ── Состояние ───────────────────────────────────────────────────

    public ViewHeader header() {
        return new ViewHeader(id, modelId, folderId, archiId, archiType, name, documentation, viewpoint, properties,
                sortOrder, rawXml, version);
    }

    public ViewId id() {
        return id;
    }

    public ModelId modelId() {
        return modelId;
    }

    public ArchiId archiId() {
        return archiId;
    }

    public String name() {
        return name;
    }

    public long version() {
        return version;
    }

    public boolean fresh() {
        return fresh;
    }

    public boolean headerChanged() {
        return fresh || headerChanged;
    }

    public TrackedMap<ViewNodeId, ViewNode> nodes() {
        return nodes;
    }

    public TrackedMap<ViewEdgeId, ViewEdge> edges() {
        return edges;
    }

    public boolean changed() {
        return headerChanged() || nodes.changed() || edges.changed();
    }
}
