package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
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
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.AcquireLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PurgeModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReleaseLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RestoreModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetAccessListQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetLockQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListModelsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ValidateModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.AccessListApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.LockApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Модели: список, дерево, жизненный цикл, блокировка, отчёт валидации, список доступа.
 */
@RestController
@RequestMapping("/api/v1")
public class ModelController {

    private final ModelQueryApplicationService queries;
    private final ModelApplicationService lifecycle;
    private final LockApplicationService locks;
    private final AccessListApplicationService accessLists;
    private final WorkspaceApplicationService workspaces;

    public ModelController(ModelQueryApplicationService queries, ModelApplicationService lifecycle,
                           LockApplicationService locks, AccessListApplicationService accessLists,
                           WorkspaceApplicationService workspaces) {
        this.queries = queries;
        this.lifecycle = lifecycle;
        this.locks = locks;
        this.accessLists = accessLists;
        this.workspaces = workspaces;
    }

    /** Рабочие пространства. */
    @GetMapping("/workspaces")
    public List<WorkspaceDto> workspaces() {
        return workspaces.listWorkspaces().stream().map(DtoMapper::workspace).toList();
    }

    /** Без {@code workspaceId} — первое пространство: до мультиарендности оно одно. */
    @GetMapping("/models")
    public List<ModelSummary> list(EditorIdentity actor, @RequestParam(required = false) UUID workspaceId) {
        return queries.listModels(actor, new ListModelsQuery(workspaceId)).stream().map(DtoMapper::summary).toList();
    }

    /** Создаёт модель — {@code 201} с адресом; без {@code workspaceId} — в первом пространстве. */
    @PostMapping("/models")
    public ResponseEntity<ModelSummary> create(EditorIdentity actor, @Valid @RequestBody CreateModelRequest request,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        ModelSummaryResponse model = lifecycle.createModel(actor,
                new CreateModelCommand(request.workspaceId(), request.name(), key));
        return ResponseEntity.created(URI.create("/api/v1/models/" + model.id())).body(DtoMapper.summary(model));
    }

    /** Открывает модель: дерево, элементы, связи и размещения на представлениях. */
    @GetMapping("/models/{id}")
    public ModelTree open(EditorIdentity actor, @PathVariable UUID id) {
        return DtoMapper.tree(queries.getModel(actor, new GetModelQuery(id)));
    }

    /** Правит имя и описание модели; не переданное поле не меняется. */
    @PatchMapping("/models/{id}")
    public ModelSummary edit(EditorIdentity actor, @PathVariable UUID id, @Valid @RequestBody ModelPatch patch) {
        return DtoMapper.summary(lifecycle.editModel(actor, new EditModelCommand(id, patch.name(),
                patch.documentation())));
    }

    /** Мягко удаляет модель: данные и история остаются. */
    @DeleteMapping("/models/{id}")
    public ResponseEntity<Void> delete(EditorIdentity actor, @PathVariable UUID id) {
        lifecycle.deleteModel(actor, new DeleteModelCommand(id));
        return ResponseEntity.noContent().build();
    }

    /** Возвращает мягко удалённую модель в работу. */
    @PostMapping("/models/{id}/restore")
    public ModelSummary restore(EditorIdentity actor, @PathVariable UUID id) {
        return DtoMapper.summary(lifecycle.restoreModel(actor, new RestoreModelCommand(id)));
    }

    /** Физически уничтожает удалённую модель. */
    @PostMapping("/models/{id}/purge")
    public ResponseEntity<Void> purge(EditorIdentity actor, @PathVariable UUID id) {
        lifecycle.purgeModel(actor, new PurgeModelCommand(id));
        return ResponseEntity.noContent().build();
    }

    // ── Блокировка ──────────────────────────────────────────────────

    /** Текущая блокировка модели; нет блокировки — {@code 204}. */
    @GetMapping("/models/{id}/lock")
    public ResponseEntity<LockInfo> lock(EditorIdentity actor, @PathVariable UUID id) {
        return locks.getLock(actor, new GetLockQuery(id)).map(l -> ResponseEntity.ok(DtoMapper.lock(l)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Захватывает или продлевает блокировку модели на запись. */
    @PostMapping("/models/{id}/lock")
    public LockInfo acquire(EditorIdentity actor, @PathVariable UUID id) {
        return DtoMapper.lock(locks.acquireLock(actor, new AcquireLockCommand(id)));
    }

    /** Снимает блокировку модели. */
    @DeleteMapping("/models/{id}/lock")
    public ResponseEntity<Void> release(EditorIdentity actor, @PathVariable UUID id) {
        locks.releaseLock(actor, new ReleaseLockCommand(id));
        return ResponseEntity.noContent().build();
    }

    // ── Валидация и доступ ──────────────────────────────────────────

    /** Находки проверки модели. */
    @GetMapping("/models/{id}/validate")
    public List<Finding> validate(EditorIdentity actor, @PathVariable UUID id) {
        return queries.validateModel(actor, new ValidateModelQuery(id)).stream().map(DtoMapper::finding).toList();
    }

    /** Список доступа модели. */
    @GetMapping("/models/{id}/acl")
    public List<AclEntryDto> acl(EditorIdentity actor, @PathVariable UUID id) {
        return DtoMapper.accessList(accessLists.getAccessList(actor, new GetAccessListQuery(id)));
    }

    /** Заменяет список доступа модели целиком и возвращает итог. */
    @PutMapping("/models/{id}/acl")
    public List<AclEntryDto> replaceAcl(EditorIdentity actor, @PathVariable UUID id,
                                        @Valid @RequestBody List<@NotNull @Valid AclEntryDto> entries) {
        return DtoMapper.accessList(accessLists.replaceAccessList(actor, DtoMapper.replaceAccessList(id, entries)));
    }
}
