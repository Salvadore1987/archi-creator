package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateViewCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ViewDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/** Новое пустое представление; без папки — в корень представлений, повтор ключа — то же представление. */
public final class ViewCreateCommandHandler {

    private final ModelingKernel kernel;

    public ViewCreateCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Представление с идентификаторами клиента, если он их выбрал; занятые — {@code 409}. */
    public ViewSummaryResponse handle(EditorIdentity actor, CreateViewCommand command) {
        return kernel.run(Operation.CREATE_VIEW, actor, () -> {
            ModelId modelId = ModelId.of(command.modelId());
            FolderId folderId = command.folderId() == null ? null : FolderId.of(command.folderId());
            RequestedIds ids = RequestedIds.of(command.id(), command.archiId());
            return kernel.unitOfWork().write(() -> kernel.idempotent("CreateView", actor, command.idempotencyKey(),
                    IdempotentCommand.fingerprint(modelId, command.name(), folderId, ids.id(), ids.archiId()),
                    () -> {
                        ArchitectureModel model = kernel.writableModel(modelId, actor);
                        FolderId folder = folderId != null ? folderId : model.roots().get(FolderType.DIAGRAMS).id();
                        ViewRef place = model.placeNewView(ViewId.of(kernel.newId(ids)), DiagramType.DIAGRAM_MODEL,
                                command.name(), folder, kernel.newArchiId(model, ids), kernel.now());
                        kernel.save(model);
                        View view = View.create(modelId, place);
                        kernel.views().save(view);
                        return view.id().toString();
                    },
                    ref -> ViewDataMapper.summary(kernel.views().load(ViewId.of(ref)).orElseThrow())));
        });
    }
}
