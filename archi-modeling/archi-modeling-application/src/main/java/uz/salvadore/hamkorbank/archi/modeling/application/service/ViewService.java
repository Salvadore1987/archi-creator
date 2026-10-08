package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** UC-MDL-002, вторая половина: представления и размещение на них (FR-11, FR-17). */
public final class ViewService {

    private final ModelingKernel kernel;

    public ViewService(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    public View create(EditorIdentity actor, ModelId modelId, String name, Optional<FolderId> folderId,
                       Optional<String> idempotencyKey) {
        return kernel.run(Operation.CREATE_VIEW, actor, () -> kernel.unitOfWork.write(() ->
                kernel.idempotent("CreateView", actor, idempotencyKey,
                        IdempotentCommand.fingerprint(modelId, name, folderId),
                        () -> {
                            ArchitectureModel model = kernel.writableModel(modelId, actor);
                            FolderId folder = folderId.orElseGet(() -> model.roots().get(FolderType.DIAGRAMS).id());
                            ViewRef place = model.placeNewView(ViewId.next(kernel.uuids), DiagramType.DIAGRAM_MODEL,
                                    name, folder, kernel.newArchiId(model), kernel.now());
                            kernel.save(model);
                            View view = View.create(modelId, place);
                            kernel.views.save(view);
                            return view.id().toString();
                        },
                        ref -> kernel.views.load(ViewId.of(ref)).orElseThrow())));
    }

    /** Разместить элемент; уже размещённый не дублируется (UI-002). */
    public ViewNode place(EditorIdentity actor, ViewId viewId, ElementId elementId, Bounds bounds,
                          Optional<ViewNodeId> parentId) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = loadView(viewId);
            ArchitectureModel model = kernel.writableModel(view.modelId(), actor);
            ViewNode node = view.placeElement(model, elementId, bounds, parentId, ViewNodeId.next(kernel.uuids),
                    kernel.archiIds.next()).value();
            kernel.views.save(view);
            return node;
        }));
    }

    /** Убрать узел с представления; элемент остаётся в модели. */
    public void removeNode(EditorIdentity actor, ViewNodeId nodeId) {
        kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = loadView(kernel.views.viewOfNode(nodeId)
                    .orElseThrow(() -> ModelingException.notFound("узел представления " + nodeId)));
            kernel.writableModel(view.modelId(), actor);
            view.removeNode(nodeId);
            kernel.views.save(view);
            return null;
        }));
    }

    /** Геометрия после перемещений одной транзакцией (SaveViewLayout, NFR-03). */
    public View saveLayout(EditorIdentity actor, ViewId viewId, Map<ViewNodeId, Bounds> nodeBounds,
                           Map<ViewEdgeId, List<Bendpoint>> edgeBendpoints) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork.write(() -> {
            View view = loadView(viewId);
            kernel.writableModel(view.modelId(), actor);
            view.applyLayout(nodeBounds, edgeBendpoints);
            kernel.views.save(view);
            return view;
        }));
    }

    private View loadView(ViewId viewId) {
        return kernel.views.load(viewId).orElseThrow(() -> ModelingException.notFound("представление " + viewId));
    }
}
