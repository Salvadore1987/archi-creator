package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/** Создать, переименовать, удалить, восстановить и уничтожить модель. */
public final class ModelLifecycleService {

    private final ModelingKernel kernel;
    private final WorkspaceRepository workspaces;
    private final VersionService versions;

    public ModelLifecycleService(ModelingKernel kernel, WorkspaceRepository workspaces, VersionService versions) {
        this.kernel = kernel;
        this.workspaces = workspaces;
        this.versions = versions;
    }

    /**
     * Пустая модель с девятью корневыми папками и версией 1 — точкой отсчёта истории.
     * Повтор с тем же ключом возвращает ту же модель.
     */
    public ArchitectureModel create(EditorIdentity actor, WorkspaceId workspaceId, String name,
                                    Optional<String> idempotencyKey) {
        return kernel.run(Operation.CREATE_MODEL, actor, () -> kernel.unitOfWork.write(() ->
                kernel.idempotent("CreateModel", actor, idempotencyKey,
                        IdempotentCommand.fingerprint(workspaceId, name),
                        () -> {
                            workspaces.find(workspaceId)
                                    .orElseThrow(() -> ModelingException.notFound("рабочее пространство " + workspaceId));
                            ArchitectureModel model = ArchitectureModel.create(ModelId.next(kernel.uuids), workspaceId,
                                    kernel.archiIds.next(), name, actor, kernel.now(),
                                    () -> FolderId.next(kernel.uuids), kernel.archiIds::next);
                            kernel.save(model);
                            versions.commit(model, actor, Optional.of("Создание модели"));
                            return model.id().toString();
                        },
                        ref -> kernel.models.load(ModelId.of(ref)).orElseThrow())));
    }

    /** PATCH: переименование и документация; пустая документация её снимает. */
    public ArchitectureModel edit(EditorIdentity actor, ModelId modelId, Optional<String> name,
                                  Optional<String> documentation) {
        return kernel.run(Operation.RENAME_MODEL, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(modelId, actor);
            name.ifPresent(n -> model.rename(n, kernel.now()));
            documentation.ifPresent(d -> model.changeDocumentation(Optional.of(d), kernel.now()));
            kernel.save(model);
            return model;
        }));
    }

    /** Soft delete: данные и история остаются, блокировка снимается. */
    public void delete(EditorIdentity actor, ModelId modelId) {
        kernel.run(Operation.DELETE_MODEL, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.visibleModel(modelId, actor, AclAccess.WRITE);
            model.requireActive();
            ModelLock.requireWriteAccess(modelId, kernel.locks.find(modelId), actor.subject(), kernel.now());
            model.delete(actor, kernel.now());
            kernel.save(model);
            ModelLock lock = kernel.locks.find(modelId).orElseThrow();
            kernel.locks.delete(modelId);
            kernel.events.publish(List.of(lock.release(actor, kernel.now())));
            return null;
        }));
    }

    public ArchitectureModel restore(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.RESTORE_MODEL, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.visibleModel(modelId, actor, AclAccess.WRITE);
            model.restoreDeleted(kernel.now());
            kernel.save(model);
            return model;
        }));
    }

    /** Физическое уничтожение — только из {@code DELETED} и только {@code ADMIN}. */
    public void purge(EditorIdentity actor, ModelId modelId) {
        kernel.run(Operation.PURGE_MODEL, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.visibleModel(modelId, actor, AclAccess.WRITE);
            model.purge(kernel.now());
            kernel.models.purge(modelId);
            kernel.events.publish(model.pullEvents());
            return null;
        }));
    }
}
