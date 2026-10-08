package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.ModelAccessList;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.LockStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.validation.ModelValidator;
import uz.salvadore.hamkorbank.archi.modeling.domain.validation.ValidationFinding;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/** Запросы modeling: список моделей, дерево, представление, отчёт валидации. */
public final class ModelQueryService {

    private final ModelingKernel kernel;
    private final ModelValidator validator = ModelValidator.archimate32();

    public ModelQueryService(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /**
     * Модели пространства, которые автор видит (INV-MDL-011). Удалённые — только
     * администратору: им их и восстанавливать.
     */
    public List<ModelHeader> list(EditorIdentity actor, WorkspaceId workspaceId) {
        return kernel.run(Operation.OPEN_MODEL, actor, () -> kernel.unitOfWork.read(() -> {
            List<ModelHeader> headers = kernel.models.list(workspaceId);
            Map<ModelId, ModelAccessList> acl = kernel.accessLists.findAll(headers.stream().map(ModelHeader::id).toList());
            return headers.stream()
                    .filter(h -> h.status() == ModelStatus.ACTIVE || actor.isAdmin())
                    .filter(h -> acl.getOrDefault(h.id(), ModelAccessList.open(h.id())).permits(actor, AclAccess.READ))
                    .toList();
        }));
    }

    /** Дерево модели: папки, элементы, связи, список представлений (OpenModel, NFR-01). */
    public ArchitectureModel open(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.OPEN_MODEL, actor,
                () -> kernel.unitOfWork.read(() -> kernel.visibleModel(modelId, actor, AclAccess.READ)));
    }

    /** Payload представления по требованию (OpenView). */
    public View openView(EditorIdentity actor, ViewId viewId) {
        return kernel.run(Operation.OPEN_VIEW, actor, () -> kernel.unitOfWork.read(() -> {
            View view = kernel.views.load(viewId).orElseThrow(() -> ModelingException.notFound("представление " + viewId));
            kernel.visibleHeader(view.modelId(), actor, AclAccess.READ);
            return view;
        }));
    }

    /** Отчёт валидации метамодели без ИИ (FR-10): импортированные нарушения видны здесь. */
    public List<ValidationFinding> validate(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.VALIDATE_MODEL, actor, () -> kernel.unitOfWork.read(
                () -> validator.validate(kernel.visibleModel(modelId, actor, AclAccess.READ))));
    }

    /** Действующая блокировка — чтобы остальные видели модель read-only с владельцем (UC-MDL-005). */
    public Optional<ModelLock> lock(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.OPEN_MODEL, actor, () -> kernel.unitOfWork.read(() -> {
            kernel.visibleHeader(modelId, actor, AclAccess.READ);
            return kernel.locks.find(modelId).filter(l -> l.status(kernel.now()) == LockStatus.HELD);
        }));
    }
}
