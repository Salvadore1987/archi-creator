package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.AutoLayoutRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.BendpointDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.CreateViewRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.EditEdgeRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.EditNodeRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.LayoutPatch;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.MoveNodeRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.PlaceEdgeRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.PlaceNodeRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.StyleDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewEdgeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewNodeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewPayload;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewSummary;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateViewCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.MoveViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RemoveViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RemoveViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveViewLayoutCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetViewQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ProposeViewLayoutQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlaceViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlaceViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Представления: payload, геометрия, размещение. */
@RestController
@RequestMapping("/api/v1")
public class ViewController {

    private final ViewQueryApplicationService queries;
    private final ViewApplicationService views;

    public ViewController(ViewQueryApplicationService queries, ViewApplicationService views) {
        this.queries = queries;
        this.views = views;
    }

    /** Создаёт представление — {@code 201} с адресом. */
    @PostMapping("/models/{id}/views")
    public ResponseEntity<ViewSummary> create(EditorIdentity actor, @PathVariable UUID id,
                                              @Valid @RequestBody CreateViewRequest request,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        ViewSummaryResponse view = views.createView(actor, new CreateViewCommand(id, request.name(),
                request.folderId(), request.id(), request.archiId(), key));
        return ResponseEntity.created(URI.create("/api/v1/views/" + view.id())).body(DtoMapper.view(view));
    }

    /** Открывает представление: узлы, рёбра и их оформление. */
    @GetMapping("/views/{id}")
    public ViewPayload open(EditorIdentity actor, @PathVariable UUID id) {
        return DtoMapper.payload(queries.getView(actor, new GetViewQuery(id)));
    }

    /** Сохраняет границы узлов и точки перегиба рёбер — {@code 204}. */
    @PutMapping("/views/{id}/layout")
    public ResponseEntity<Void> layout(EditorIdentity actor, @PathVariable UUID id,
                                       @Valid @RequestBody LayoutPatch patch) {
        views.saveLayout(actor, new SaveViewLayoutCommand(id,
                Optional.ofNullable(patch.nodes()).orElse(List.of()).stream()
                        .map(n -> new SaveViewLayoutCommand.NodeBounds(n.id(), n.x(), n.y(), n.width(), n.height()))
                        .toList(),
                Optional.ofNullable(patch.edges()).orElse(List.of()).stream()
                        .map(e -> new SaveViewLayoutCommand.EdgeBendpoints(e.id(), bendpoints(e.bendpoints())))
                        .toList()));
        return ResponseEntity.noContent().build();
    }

    /** Предложение авторазметки; база не меняется. */
    @PostMapping("/views/{id}/auto-layout")
    public LayoutPatch autoLayout(EditorIdentity actor, @PathVariable UUID id,
                                  @Valid @RequestBody AutoLayoutRequest request) {
        return DtoMapper.layoutPatch(queries.proposeLayout(actor, layoutQuery(id, request)));
    }

    /**
     * Новый узел — {@code 201}; элемент уже размещён на представлении — {@code 200} с его узлом.
     * Группа и заметка создаются всегда.
     */
    @PostMapping("/views/{id}/nodes")
    public ResponseEntity<ViewNodeDto> place(EditorIdentity actor, @PathVariable UUID id,
                                             @Valid @RequestBody PlaceNodeRequest request) {
        PlaceViewNodeResponse node = views.placeNode(actor, new PlaceViewNodeCommand(id, request.kind(),
                request.elementId(), request.label(), request.content(), request.x(), request.y(), request.width(),
                request.height(), request.parentId(), request.id(), request.archiId()));
        ViewNodeDto body = DtoMapper.node(node.node());
        return node.created()
                ? ResponseEntity.created(URI.create("/api/v1/view-nodes/" + body.id())).body(body)
                : ResponseEntity.ok(body);
    }

    /** Переносит узел в другой родительский узел или на верхний уровень представления. */
    @PutMapping("/view-nodes/{id}/parent")
    public ViewNodeDto moveToParent(EditorIdentity actor, @PathVariable UUID id,
                                    @Valid @RequestBody MoveNodeRequest request) {
        return DtoMapper.node(views.moveNode(actor,
                new MoveViewNodeCommand(id, request.parentId(), request.x(), request.y())));
    }

    /** Правит подпись, текст и собственный стиль узла. */
    @PatchMapping("/view-nodes/{id}")
    public ViewNodeDto editNode(EditorIdentity actor, @PathVariable UUID id,
                                @Valid @RequestBody EditNodeRequest request) {
        return DtoMapper.node(views.editNode(actor, new EditViewNodeCommand(id, request.label(), request.content(),
                style(request.style()))));
    }

    /** Правит собственный стиль ребра. */
    @PatchMapping("/view-edges/{id}")
    public ViewEdgeDto editEdge(EditorIdentity actor, @PathVariable UUID id,
                                @Valid @RequestBody EditEdgeRequest request) {
        return DtoMapper.edge(views.editEdge(actor, new EditViewEdgeCommand(id, style(request.style()))));
    }

    /** Удаляет ребро с представления; связь в модели остаётся. */
    @DeleteMapping("/view-edges/{id}")
    public ResponseEntity<Void> removeEdge(EditorIdentity actor, @PathVariable UUID id) {
        views.removeEdge(actor, new RemoveViewEdgeCommand(id));
        return ResponseEntity.noContent().build();
    }

    /** Новое ребро — {@code 201}; такое же ребро между теми же концами уже есть — {@code 200} с ним. */
    @PostMapping("/views/{id}/edges")
    public ResponseEntity<ViewEdgeDto> placeEdge(EditorIdentity actor, @PathVariable UUID id,
                                                 @Valid @RequestBody PlaceEdgeRequest request) {
        PlaceViewEdgeResponse edge = views.placeEdge(actor, new PlaceViewEdgeCommand(id, request.relationshipId(),
                request.sourceId(), request.targetId(), bendpoints(request.bendpoints()), request.id(),
                request.archiId()));
        ViewEdgeDto body = DtoMapper.edge(edge.edge());
        return edge.created() ? ResponseEntity.status(HttpStatus.CREATED).body(body) : ResponseEntity.ok(body);
    }

    /** Удаляет узел с представления; элемент в модели остаётся. */
    @DeleteMapping("/view-nodes/{id}")
    public ResponseEntity<Void> remove(EditorIdentity actor, @PathVariable UUID id) {
        views.removeNode(actor, new RemoveViewNodeCommand(id));
        return ResponseEntity.noContent().build();
    }

    private static List<BendpointValue> bendpoints(List<BendpointDto> points) {
        return Optional.ofNullable(points).orElse(List.of()).stream()
                .map(b -> new BendpointValue(b.startX(), b.startY(), b.endX(), b.endY())).toList();
    }

    private static ProposeViewLayoutQuery layoutQuery(UUID viewId, AutoLayoutRequest request) {
        List<ProposeViewLayoutQuery.Node> nodes = request.nodes().stream()
                .map(n -> new ProposeViewLayoutQuery.Node(n.id(), n.parentId(), n.conceptType(), n.x(), n.y(),
                        n.width(), n.height()))
                .toList();
        List<ProposeViewLayoutQuery.Edge> edges = Optional.ofNullable(request.edges()).orElse(List.of()).stream()
                .map(e -> new ProposeViewLayoutQuery.Edge(e.id(), e.sourceId(), e.targetId()))
                .toList();
        return new ProposeViewLayoutQuery(viewId, request.strategy(), nodes, edges);
    }

    private static StyleValue style(StyleDto dto) {
        return dto == null ? null
                : new StyleValue(dto.fillColor(), dto.font(), dto.fontColor(), dto.lineColor(), dto.textAlignment());
    }
}
