package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.BendpointDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.CreateViewRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.LayoutPatch;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.PlaceEdgeRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.PlaceNodeRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewEdgeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewNodeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewPayload;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewSummary;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.RequestedIds;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ViewService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Представления: payload, геометрия, размещение. */
@RestController
@RequestMapping("/api/v1")
public class ViewController {

    /** Поля запроса по их именам в JSON — в сообщении о том, чего не хватает. */
    private static final String ELEMENT_ID = "elementId";
    private static final String EDGE_FIELDS = "relationshipId, sourceId, targetId";

    private final ModelQueryService queries;
    private final ViewService views;

    public ViewController(ModelQueryService queries, ViewService views) {
        this.queries = queries;
        this.views = views;
    }

    @PostMapping("/models/{id}/views")
    public ResponseEntity<ViewSummary> create(EditorIdentity actor, @PathVariable UUID id,
                                              @RequestBody CreateViewRequest request,
                                              @RequestHeader("Idempotency-Key") Optional<String> key) {
        var view = views.create(actor, ModelId.of(id), request.name(),
                Optional.ofNullable(request.folderId()).map(FolderId::of),
                RequestedIds.of(request.id(), request.archiId()), key);
        return ResponseEntity.created(URI.create("/api/v1/views/" + view.id())).body(DtoMapper.view(view));
    }

    @GetMapping("/views/{id}")
    public ViewPayload open(EditorIdentity actor, @PathVariable UUID id) {
        return DtoMapper.payload(queries.openView(actor, ViewId.of(id)));
    }

    @PutMapping("/views/{id}/layout")
    public ResponseEntity<Void> layout(EditorIdentity actor, @PathVariable UUID id, @RequestBody LayoutPatch patch) {
        Map<ViewNodeId, Bounds> nodes = new LinkedHashMap<>();
        Optional.ofNullable(patch.nodes()).orElse(List.of()).forEach(n -> nodes.put(ViewNodeId.of(n.id()),
                bounds(n.x(), n.y(), n.width(), n.height())));
        Map<ViewEdgeId, List<Bendpoint>> edges = new LinkedHashMap<>();
        Optional.ofNullable(patch.edges()).orElse(List.of())
                .forEach(e -> edges.put(ViewEdgeId.of(e.id()), bendpoints(e.bendpoints())));
        views.saveLayout(actor, ViewId.of(id), nodes, edges);
        return ResponseEntity.noContent().build();
    }

    /** Новый узел — {@code 201}; элемент уже размещён на представлении — {@code 200} с его узлом. */
    @PostMapping("/views/{id}/nodes")
    public ResponseEntity<ViewNodeDto> place(EditorIdentity actor, @PathVariable UUID id,
                                             @RequestBody PlaceNodeRequest request) {
        if (request.elementId() == null) {
            throw ModelingException.invalid(Message.of(ModelingMessages.FIELD_MISSING, ELEMENT_ID));
        }
        var node = views.place(actor, ViewId.of(id), ElementId.of(request.elementId()),
                newElementBounds(request.x(), request.y(), Optional.ofNullable(request.width()),
                        Optional.ofNullable(request.height())),
                Optional.ofNullable(request.parentId()).map(ViewNodeId::of),
                RequestedIds.of(request.id(), request.archiId()));
        ViewNodeDto body = DtoMapper.node(node.value());
        return node.created()
                ? ResponseEntity.created(URI.create("/api/v1/view-nodes/" + body.id())).body(body)
                : ResponseEntity.ok(body);
    }

    /** Новое ребро — {@code 201}; такое же ребро между теми же концами уже есть — {@code 200} с ним. */
    @PostMapping("/views/{id}/edges")
    public ResponseEntity<ViewEdgeDto> placeEdge(EditorIdentity actor, @PathVariable UUID id,
                                                 @RequestBody PlaceEdgeRequest request) {
        if (request.relationshipId() == null || request.sourceId() == null || request.targetId() == null) {
            throw ModelingException.invalid(Message.of(ModelingMessages.FIELDS_MISSING, EDGE_FIELDS));
        }
        var edge = views.placeEdge(actor, ViewId.of(id), RelationshipId.of(request.relationshipId()),
                request.sourceId(), request.targetId(), bendpoints(request.bendpoints()),
                RequestedIds.of(request.id(), request.archiId()));
        ViewEdgeDto body = DtoMapper.edge(edge.value());
        return edge.created() ? ResponseEntity.status(HttpStatus.CREATED).body(body) : ResponseEntity.ok(body);
    }

    @DeleteMapping("/view-nodes/{id}")
    public ResponseEntity<Void> remove(EditorIdentity actor, @PathVariable UUID id) {
        views.removeNode(actor, ViewNodeId.of(id));
        return ResponseEntity.noContent().build();
    }

    private static List<Bendpoint> bendpoints(List<BendpointDto> points) {
        return Optional.ofNullable(points).orElse(List.of()).stream()
                .map(b -> new Bendpoint(b.startX(), b.startY(), b.endX(), b.endY())).toList();
    }

    private static Bounds newElementBounds(int x, int y, Optional<Integer> width, Optional<Integer> height) {
        try {
            return Bounds.ofNewElement(x, y, width, height);
        } catch (IllegalArgumentException invalid) {
            throw ModelingException.invalid(invalid);
        }
    }

    private static Bounds bounds(int x, int y, int width, int height) {
        try {
            return new Bounds(x, y, width, height);
        } catch (IllegalArgumentException invalid) {
            throw ModelingException.invalid(invalid);
        }
    }
}
