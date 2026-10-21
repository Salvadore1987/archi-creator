package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.RelationTypeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.RelationshipDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.RenameRequest;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateFolderCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteTreeItemsCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.MoveTreeItemsCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RenameTreeItemCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.SuggestRelationTypesQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CreateRelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ElementApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.MetamodelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.RelationshipApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.TreeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Содержимое модели: элементы, связи, дерево папок, метамодель для палитры.
 */
@RestController
@RequestMapping("/api/v1")
public class ContentController {

    private final ElementApplicationService elements;
    private final RelationshipApplicationService relationships;
    private final TreeApplicationService tree;
    private final MetamodelApplicationService metamodel;

    public ContentController(ElementApplicationService elements, RelationshipApplicationService relationships,
                             TreeApplicationService tree, MetamodelApplicationService metamodel) {
        this.elements = elements;
        this.relationships = relationships;
        this.tree = tree;
        this.metamodel = metamodel;
    }

    // ── Элементы ────────────────────────────────────────────────────

    /** Создаёт элемент в модели — {@code 201} с адресом нового элемента. */
    @PostMapping("/models/{id}/elements")
    public ResponseEntity<ElementDto> createElement(
            EditorIdentity actor, @PathVariable UUID id, @Valid @RequestBody CreateElementRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        ElementResponse element = elements.createElement(actor, new CreateElementCommand(id, request.archiType(),
                request.name(), request.folderId(), request.id(), request.archiId(), key));
        return ResponseEntity.created(URI.create("/api/v1/elements/" + element.id())).body(DtoMapper.element(element));
    }

    /** Правит имя, описание и свойства элемента; не переданное поле не меняется. */
    @PatchMapping("/elements/{id}")
    public ElementDto updateElement(EditorIdentity actor, @PathVariable UUID id,
                                    @Valid @RequestBody ElementPatch patch) {
        return DtoMapper.element(elements.editElement(actor, new EditElementCommand(id, patch.name(),
                patch.documentation(), DtoMapper.properties(patch.properties()))));
    }

    /** Со связями — {@code 409} с их перечнем в {@code relationships}. */
    @DeleteMapping("/elements/{id}")
    public ResponseEntity<Void> deleteElement(EditorIdentity actor, @PathVariable UUID id) {
        elements.deleteElement(actor, new DeleteElementCommand(id));
        return ResponseEntity.noContent().build();
    }

    // ── Связи ───────────────────────────────────────────────────────

    /** Новая — {@code 201}; дубль того же типа между той же парой — {@code 200} с существующей. */
    @PostMapping("/models/{id}/relationships")
    public ResponseEntity<RelationshipDto> createRelationship(
            EditorIdentity actor, @PathVariable UUID id, @Valid @RequestBody CreateRelationshipRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        CreateRelationshipResponse result = relationships.createRelationship(actor, new CreateRelationshipCommand(id,
                request.archiType(), request.sourceId(), request.targetId(),
                request.name(), request.folderId(), request.accessType(), request.directed(), request.id(),
                request.archiId(), DtoMapper.edge(request.view()), key));
        RelationshipDto body = DtoMapper.relationship(result.relationship(), result.edgeId());
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/relationships/" + body.id())).body(body)
                : ResponseEntity.ok(body);
    }

    /** Правит имя, описание и свойства связи; не переданное поле не меняется. */
    @PatchMapping("/relationships/{id}")
    public RelationshipDto updateRelationship(EditorIdentity actor, @PathVariable UUID id,
                                              @Valid @RequestBody ElementPatch patch) {
        return DtoMapper.relationship(relationships.editRelationship(actor, new EditRelationshipCommand(id,
                patch.name(), patch.documentation(), DtoMapper.properties(patch.properties()))), null);
    }

    /** Удаляет связь вместе с её рёбрами на представлениях. */
    @DeleteMapping("/relationships/{id}")
    public ResponseEntity<Void> deleteRelationship(EditorIdentity actor, @PathVariable UUID id) {
        relationships.deleteRelationship(actor, new DeleteRelationshipCommand(id));
        return ResponseEntity.noContent().build();
    }

    // ── Дерево ──────────────────────────────────────────────────────

    /** Создаёт папку в дереве модели — {@code 201} с адресом папки в дереве. */
    @PostMapping("/models/{id}/folders")
    public ResponseEntity<FolderDto> createFolder(
            EditorIdentity actor, @PathVariable UUID id, @Valid @RequestBody CreateFolderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        FolderDto folder = DtoMapper.folder(tree.createFolder(actor, new CreateFolderCommand(id, request.parentId(),
                request.name(), request.id(), request.archiId(), key)));
        return ResponseEntity.created(URI.create("/api/v1/models/" + id + "/tree/" + folder.id())).body(folder);
    }

    /** Переименовывает папку, элемент, связь или представление в дереве. */
    @PatchMapping("/models/{id}/tree/{itemId}")
    public ResponseEntity<Void> rename(EditorIdentity actor, @PathVariable UUID id, @PathVariable UUID itemId,
                                       @Valid @RequestBody RenameRequest request) {
        tree.renameTreeItem(actor, new RenameTreeItemCommand(id, itemId, request.name()));
        return ResponseEntity.noContent().build();
    }

    /** Переносит объекты дерева в папку. */
    @PostMapping("/models/{id}/tree/move")
    public ResponseEntity<Void> move(EditorIdentity actor, @PathVariable UUID id,
                                     @Valid @RequestBody MoveRequest request) {
        tree.moveTreeItems(actor, new MoveTreeItemsCommand(id, request.targetFolderId(), request.itemIds()));
        return ResponseEntity.noContent().build();
    }

    /** Групповое удаление — одна транзакция: часть не проходит — не удаляется ничего. */
    @PostMapping("/models/{id}/tree/delete")
    public ResponseEntity<Void> deleteItems(EditorIdentity actor, @PathVariable UUID id,
                                            @Valid @RequestBody ItemsRequest request) {
        tree.deleteTreeItems(actor, new DeleteTreeItemsCommand(id, request.itemIds()));
        return ResponseEntity.noContent().build();
    }

    // ── Метамодель ──────────────────────────────────────────────────

    /** Каталог типов для палитры: что редактируется в текущей фазе. */
    @GetMapping("/metamodel/elements")
    public List<ElementTypeDto> elementTypes() {
        return metamodel.listElementTypes().stream().map(DtoMapper::elementType).toList();
    }

    /** Допустимые связи для пары типов; первая — выбор по умолчанию, подразумеваемая вложением помечена. */
    @GetMapping("/metamodel/relations")
    public List<RelationTypeDto> relationTypes(@RequestParam String source, @RequestParam String target) {
        return metamodel.suggestRelationTypes(new SuggestRelationTypesQuery(source, target)).stream()
                .map(DtoMapper::relationType).toList();
    }

}
