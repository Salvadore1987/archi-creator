package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.CreateElementRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.CreateFolderRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.CreateRelationshipRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ElementDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ElementPatch;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ElementTypeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.FolderDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ItemsRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.MoveRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.PropertyDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.RelationTypeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.RelationshipDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.RenameRequest;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ElementService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.RelationshipService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.TreeService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationshipType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/**
 * Содержимое модели: элементы, связи, дерево папок, метамодель для палитры.
 */
@RestController
@RequestMapping("/api/v1")
public class ContentController {

    private final ElementService elements;
    private final RelationshipService relationships;
    private final TreeService tree;

    public ContentController(ElementService elements, RelationshipService relationships, TreeService tree) {
        this.elements = elements;
        this.relationships = relationships;
        this.tree = tree;
    }

    // ── Элементы ────────────────────────────────────────────────────

    @PostMapping("/models/{id}/elements")
    public ResponseEntity<ElementDto> createElement(EditorIdentity actor, @PathVariable UUID id,
                                                    @RequestBody CreateElementRequest request,
                                                    @RequestHeader("Idempotency-Key") Optional<String> key) {
        var element = elements.create(actor, ModelId.of(id), archiType(request.archiType()), request.name(),
                Optional.ofNullable(request.folderId()).map(FolderId::of), key);
        return ResponseEntity.created(URI.create("/api/v1/elements/" + element.id())).body(DtoMapper.element(element));
    }

    @PatchMapping("/elements/{id}")
    public ElementDto updateElement(EditorIdentity actor, @PathVariable UUID id, @RequestBody ElementPatch patch) {
        return DtoMapper.element(elements.update(actor, ElementId.of(id), Optional.ofNullable(patch.name()),
                Optional.ofNullable(patch.documentation()), properties(patch.properties())));
    }

    /** Со связями — {@code 409} с их перечнем в {@code relationships}. */
    @DeleteMapping("/elements/{id}")
    public ResponseEntity<Void> deleteElement(EditorIdentity actor, @PathVariable UUID id) {
        elements.delete(actor, ElementId.of(id));
        return ResponseEntity.noContent().build();
    }

    // ── Связи ───────────────────────────────────────────────────────

    /** Новая — {@code 201}; дубль того же типа между той же парой — {@code 200} с существующей. */
    @PostMapping("/models/{id}/relationships")
    public ResponseEntity<RelationshipDto> createRelationship(EditorIdentity actor, @PathVariable UUID id,
                                                              @RequestBody CreateRelationshipRequest request,
                                                              @RequestHeader("Idempotency-Key") Optional<String> key) {
        var placement = Optional.ofNullable(request.view()).map(v -> new RelationshipService.EdgePlacement(
                ViewId.of(v.viewId()), v.sourceNodeId(), v.targetNodeId()));
        var result = relationships.create(actor, ModelId.of(id), archiType(request.archiType()),
                require(request.sourceId(), "sourceId"), require(request.targetId(), "targetId"),
                Optional.ofNullable(request.name()), placement, key);
        RelationshipDto body = DtoMapper.relationship(result.relationship(),
                result.edge().map(e -> e.value()).orElse(null));
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/relationships/" + body.id())).body(body)
                : ResponseEntity.ok(body);
    }

    @PatchMapping("/relationships/{id}")
    public RelationshipDto updateRelationship(EditorIdentity actor, @PathVariable UUID id,
                                              @RequestBody ElementPatch patch) {
        return DtoMapper.relationship(relationships.update(actor, RelationshipId.of(id),
                Optional.ofNullable(patch.name()), Optional.ofNullable(patch.documentation()),
                properties(patch.properties())), null);
    }

    @DeleteMapping("/relationships/{id}")
    public ResponseEntity<Void> deleteRelationship(EditorIdentity actor, @PathVariable UUID id) {
        relationships.delete(actor, RelationshipId.of(id));
        return ResponseEntity.noContent().build();
    }

    // ── Дерево ──────────────────────────────────────────────────────

    @PostMapping("/models/{id}/folders")
    public ResponseEntity<FolderDto> createFolder(EditorIdentity actor, @PathVariable UUID id,
                                                  @RequestBody CreateFolderRequest request,
                                                  @RequestHeader("Idempotency-Key") Optional<String> key) {
        var folder = tree.createFolder(actor, ModelId.of(id), FolderId.of(require(request.parentId(), "parentId")),
                request.name(), key);
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.folder(folder));
    }

    @PatchMapping("/models/{id}/tree/{itemId}")
    public ResponseEntity<Void> rename(EditorIdentity actor, @PathVariable UUID id, @PathVariable UUID itemId,
                                       @RequestBody RenameRequest request) {
        tree.rename(actor, ModelId.of(id), itemId, request.name());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/models/{id}/tree/move")
    public ResponseEntity<Void> move(EditorIdentity actor, @PathVariable UUID id, @RequestBody MoveRequest request) {
        tree.move(actor, ModelId.of(id), FolderId.of(require(request.targetFolderId(), "targetFolderId")),
                items(request.itemIds()));
        return ResponseEntity.noContent().build();
    }

    /** Групповое удаление — одна транзакция: часть не проходит — не удаляется ничего. */
    @PostMapping("/models/{id}/tree/delete")
    public ResponseEntity<Void> deleteItems(EditorIdentity actor, @PathVariable UUID id,
                                            @RequestBody ItemsRequest request) {
        tree.delete(actor, ModelId.of(id), items(request.itemIds()));
        return ResponseEntity.noContent().build();
    }

    // ── Метамодель ──────────────────────────────────────────────────

    /** Каталог типов для палитры: что редактируется в текущей фазе. */
    @GetMapping("/metamodel/elements")
    public List<ElementTypeDto> elementTypes() {
        ArchiTypeRegistry registry = ArchiTypeRegistry.archimate32();
        return java.util.stream.Stream.of(ConceptKind.ELEMENT, ConceptKind.JUNCTION)
                .flatMap(kind -> registry.concepts(kind).stream())
                .map(c -> new ElementTypeDto(c.type().value(), c.kind().name(), c.layer().name(),
                        c.phase().map(Enum::name).orElse(null), c.supported()))
                .toList();
    }

    /** Допустимые связи для пары типов; первая — выбор по умолчанию. */
    @GetMapping("/metamodel/relations")
    public List<RelationTypeDto> relationTypes(@RequestParam String source, @RequestParam String target) {
        List<RelationshipType> permitted = RelationshipService.suggest(archiType(source), archiType(target));
        return java.util.stream.IntStream.range(0, permitted.size())
                .mapToObj(i -> new RelationTypeDto(permitted.get(i).archiType().value(), permitted.get(i).name(),
                        i == 0))
                .toList();
    }

    static ArchiType archiType(String value) {
        try {
            return ArchiType.of(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw ModelingException.invalid("archiType вида archimate:<Имя>, получено: " + value);
        }
    }

    private static Optional<List<ElementService.PropertyValue>> properties(List<PropertyDto> properties) {
        return Optional.ofNullable(properties).map(list -> list.stream()
                .map(p -> new ElementService.PropertyValue(p.key(), p.value() == null ? "" : p.value())).toList());
    }

    private static List<UUID> items(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw ModelingException.invalid("пустой список объектов");
        }
        return ids;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw ModelingException.invalid("не задано поле " + name);
        }
        return value;
    }
}
