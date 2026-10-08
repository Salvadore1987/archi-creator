package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.AclEntryDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.CreateModelRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.Finding;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.LockInfo;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ModelPatch;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ModelSummary;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ModelTree;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.WorkspaceDto;
import uz.salvadore.hamkorbank.archi.modeling.application.service.AccessListService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.LockService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelLifecycleService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.WorkspaceService;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.PrincipalType;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Модели: список, дерево, жизненный цикл, блокировка, отчёт валидации, список доступа.
 */
@RestController
@RequestMapping("/api/v1")
public class ModelController {

    private final ModelQueryService queries;
    private final ModelLifecycleService lifecycle;
    private final LockService locks;
    private final AccessListService accessLists;
    private final WorkspaceService workspaces;

    public ModelController(ModelQueryService queries, ModelLifecycleService lifecycle, LockService locks,
                           AccessListService accessLists, WorkspaceService workspaces) {
        this.queries = queries;
        this.lifecycle = lifecycle;
        this.locks = locks;
        this.accessLists = accessLists;
        this.workspaces = workspaces;
    }

    @GetMapping("/workspaces")
    public List<WorkspaceDto> workspaces() {
        return workspaces.list().stream().map(w -> new WorkspaceDto(w.id().value(), w.name())).toList();
    }

    /** Без {@code workspaceId} — первое пространство: до мультиарендности оно одно. */
    @GetMapping("/models")
    public List<ModelSummary> list(EditorIdentity actor, @RequestParam Optional<UUID> workspaceId) {
        return queries.list(actor, workspace(workspaceId)).stream().map(DtoMapper::summary).toList();
    }

    @PostMapping("/models")
    public ResponseEntity<ModelSummary> create(EditorIdentity actor, @RequestBody CreateModelRequest request,
                                               @RequestHeader("Idempotency-Key") Optional<String> key) {
        var model = lifecycle.create(actor, workspace(Optional.ofNullable(request.workspaceId())), request.name(), key);
        return ResponseEntity.created(URI.create("/api/v1/models/" + model.id())).body(DtoMapper.summary(model.header()));
    }

    @GetMapping("/models/{id}")
    public ModelTree open(EditorIdentity actor, @PathVariable UUID id) {
        return DtoMapper.tree(queries.open(actor, ModelId.of(id)));
    }

    @PatchMapping("/models/{id}")
    public ModelSummary edit(EditorIdentity actor, @PathVariable UUID id, @RequestBody ModelPatch patch) {
        return DtoMapper.summary(lifecycle.edit(actor, ModelId.of(id), Optional.ofNullable(patch.name()),
                Optional.ofNullable(patch.documentation())).header());
    }

    @DeleteMapping("/models/{id}")
    public ResponseEntity<Void> delete(EditorIdentity actor, @PathVariable UUID id) {
        lifecycle.delete(actor, ModelId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/models/{id}/restore")
    public ModelSummary restore(EditorIdentity actor, @PathVariable UUID id) {
        return DtoMapper.summary(lifecycle.restore(actor, ModelId.of(id)).header());
    }

    @PostMapping("/models/{id}/purge")
    public ResponseEntity<Void> purge(EditorIdentity actor, @PathVariable UUID id) {
        lifecycle.purge(actor, ModelId.of(id));
        return ResponseEntity.noContent().build();
    }

    // ── Блокировка ──────────────────────────────────────────────────

    @GetMapping("/models/{id}/lock")
    public ResponseEntity<LockInfo> lock(EditorIdentity actor, @PathVariable UUID id) {
        return queries.lock(actor, ModelId.of(id)).map(l -> ResponseEntity.ok(DtoMapper.lock(l)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/models/{id}/lock")
    public LockInfo acquire(EditorIdentity actor, @PathVariable UUID id) {
        return DtoMapper.lock(locks.acquire(actor, ModelId.of(id)));
    }

    @DeleteMapping("/models/{id}/lock")
    public ResponseEntity<Void> release(EditorIdentity actor, @PathVariable UUID id) {
        locks.release(actor, ModelId.of(id));
        return ResponseEntity.noContent().build();
    }

    // ── Валидация и доступ ──────────────────────────────────────────

    @GetMapping("/models/{id}/validate")
    public List<Finding> validate(EditorIdentity actor, @PathVariable UUID id) {
        return queries.validate(actor, ModelId.of(id)).stream().map(DtoMapper::finding).toList();
    }

    @GetMapping("/models/{id}/acl")
    public List<AclEntryDto> acl(EditorIdentity actor, @PathVariable UUID id) {
        return accessLists.get(actor, ModelId.of(id)).entries().stream()
                .map(e -> new AclEntryDto(e.principalType().name(), e.principal(), e.access().name())).toList();
    }

    @PutMapping("/models/{id}/acl")
    public List<AclEntryDto> replaceAcl(EditorIdentity actor, @PathVariable UUID id,
                                        @RequestBody List<AclEntryDto> entries) {
        List<AclEntry> domain = entries.stream().map(ModelController::entry).toList();
        return accessLists.replace(actor, ModelId.of(id), domain).entries().stream()
                .map(e -> new AclEntryDto(e.principalType().name(), e.principal(), e.access().name())).toList();
    }

    private static AclEntry entry(AclEntryDto dto) {
        try {
            return new AclEntry(PrincipalType.valueOf(dto.principalType()), dto.principal(),
                    AclAccess.valueOf(dto.access()));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw ModelingException.invalid("запись списка доступа: тип USER|GROUP, уровень READ|WRITE");
        }
    }

    private WorkspaceId workspace(Optional<UUID> requested) {
        return requested.map(WorkspaceId::of).orElseGet(() -> workspaces.list().stream().findFirst()
                .orElseThrow(() -> ModelingException.notFound("рабочее пространство")).id());
    }
}
