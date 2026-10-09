package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewPlacements;
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
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
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
     * Модели пространства, которые автор видит по списку доступа. Удалённые — только
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

    /** Дерево модели: папки, элементы, связи, список представлений (OpenModel). */
    public ArchitectureModel open(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.OPEN_MODEL, actor, () -> kernel.unitOfWork.read(() -> {
            ArchitectureModel model = kernel.visibleModel(modelId, actor, AclAccess.READ);
            kernel.metrics.modelSize(modelId, model.elements().size());
            return model;
        }));
    }

    /** Дерево модели и где что размещено: по записи на каждое её представление, в порядке дерева. */
    public record OpenedModel(ArchitectureModel model, List<ViewPlacements> placements) {
    }

    /** Дерево вместе с размещениями: маркеры «не размещён» и «×N» дереву нужны сразу. */
    public OpenedModel openWithPlacements(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.OPEN_MODEL, actor, () -> kernel.unitOfWork.read(() -> {
            ArchitectureModel model = kernel.visibleModel(modelId, actor, AclAccess.READ);
            kernel.metrics.modelSize(modelId, model.elements().size());
            Map<ViewId, ViewPlacements> found = new java.util.HashMap<>();
            kernel.views.placements(modelId).forEach(p -> found.put(p.viewId(), p));
            List<ViewPlacements> placements = model.views().stream()
                    .sorted(java.util.Comparator.comparing(ViewRef::sortOrder))
                    .map(v -> found.getOrDefault(v.id(), ViewPlacements.empty(v.id())))
                    .toList();
            return new OpenedModel(model, placements);
        }));
    }

    /** Payload представления по требованию (OpenView). */
    public View openView(EditorIdentity actor, ViewId viewId) {
        return kernel.run(Operation.OPEN_VIEW, actor, () -> kernel.unitOfWork.read(() -> {
            View view = kernel.views.load(viewId).orElseThrow(() -> ModelingException.notFound("представление " + viewId));
            kernel.visibleHeader(view.modelId(), actor, AclAccess.READ);
            return view;
        }));
    }

    /** Отчёт валидации метамодели без ИИ: импортированные нарушения видны здесь. */
    public List<ValidationFinding> validate(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.VALIDATE_MODEL, actor, () -> kernel.unitOfWork.read(() -> {
            List<ValidationFinding> findings = validator.validate(kernel.visibleModel(modelId, actor, AclAccess.READ));
            kernel.metrics.validationFindings(modelId, findings.stream().collect(java.util.stream.Collectors
                    .groupingBy(f -> f.severity().name(), java.util.stream.Collectors.counting())));
            return findings;
        }));
    }

    /** Действующая блокировка — чтобы остальные видели модель read-only с владельцем. */
    public Optional<ModelLock> lock(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.OPEN_MODEL, actor, () -> kernel.unitOfWork.read(() -> {
            kernel.visibleHeader(modelId, actor, AclAccess.READ);
            return kernel.locks.find(modelId).filter(l -> l.status(kernel.now()) == LockStatus.HELD);
        }));
    }
}
