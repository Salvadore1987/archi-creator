package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.CreateViewRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.LayoutPatch;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.PlaceNodeRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewNodeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewPayload;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewSummary;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ViewService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Представления: payload, геометрия, размещение (UC-MDL-002; FR-11, FR-17). */
@RestController
@RequestMapping("/api/v1")
public class ViewController {

    /** Размер нового узла по умолчанию — как у Archi. */
    private static final int DEFAULT_WIDTH = 120;
    private static final int DEFAULT_HEIGHT = 55;

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
                Optional.ofNullable(request.folderId()).map(FolderId::of), key);
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
        Optional.ofNullable(patch.edges()).orElse(List.of()).forEach(e -> edges.put(ViewEdgeId.of(e.id()),
                Optional.ofNullable(e.bendpoints()).orElse(List.of()).stream()
                        .map(b -> new Bendpoint(b.startX(), b.startY(), b.endX(), b.endY())).toList()));
        views.saveLayout(actor, ViewId.of(id), nodes, edges);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/views/{id}/nodes")
    public ResponseEntity<ViewNodeDto> place(EditorIdentity actor, @PathVariable UUID id,
                                             @RequestBody PlaceNodeRequest request) {
        if (request.elementId() == null) {
            throw ModelingException.invalid("не задан elementId");
        }
        var node = views.place(actor, ViewId.of(id), ElementId.of(request.elementId()),
                bounds(request.x(), request.y(), Optional.ofNullable(request.width()).orElse(DEFAULT_WIDTH),
                        Optional.ofNullable(request.height()).orElse(DEFAULT_HEIGHT)),
                Optional.ofNullable(request.parentId()).map(ViewNodeId::of));
        return ResponseEntity.created(URI.create("/api/v1/view-nodes/" + node.id())).body(DtoMapper.node(node));
    }

    @DeleteMapping("/view-nodes/{id}")
    public ResponseEntity<Void> remove(EditorIdentity actor, @PathVariable UUID id) {
        views.removeNode(actor, ViewNodeId.of(id));
        return ResponseEntity.noContent().build();
    }

    private static Bounds bounds(int x, int y, int width, int height) {
        try {
            return new Bounds(x, y, width, height);
        } catch (IllegalArgumentException invalid) {
            throw ModelingException.invalid(invalid.getMessage());
        }
    }
}
