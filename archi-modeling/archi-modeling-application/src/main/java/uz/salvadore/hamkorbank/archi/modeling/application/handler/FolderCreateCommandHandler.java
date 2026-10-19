package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateFolderCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.FolderResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Новая папка в дереве модели; повтор с тем же ключом — та же папка. */
public final class FolderCreateCommandHandler {

    private final ModelingKernel kernel;

    public FolderCreateCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Папка с идентификаторами клиента, если он их выбрал; занятые — {@code 409}. */
    public FolderResponse handle(EditorIdentity actor, CreateFolderCommand command) {
        return kernel.run(Operation.REORGANIZE_TREE, actor, () -> {
            ModelId modelId = ModelId.of(command.modelId());
            FolderId parentId = FolderId.of(command.parentId());
            RequestedIds ids = RequestedIds.of(command.id(), command.archiId());
            return kernel.unitOfWork().write(() -> kernel.idempotent("CreateFolder", actor,
                    command.idempotencyKey(),
                    IdempotentCommand.fingerprint(modelId, parentId, command.name(), ids.id(), ids.archiId()),
                    () -> {
                        ArchitectureModel model = kernel.writableModel(modelId, actor);
                        ModelFolder folder = model.createFolder(parentId, command.name(),
                                FolderId.of(kernel.newId(ids)), kernel.newArchiId(model, ids), kernel.now());
                        kernel.save(model);
                        return folder.id().toString();
                    },
                    ref -> ModelDataMapper.folder(
                            kernel.models().load(modelId).orElseThrow().requireFolder(FolderId.of(ref)))));
        });
    }
}
