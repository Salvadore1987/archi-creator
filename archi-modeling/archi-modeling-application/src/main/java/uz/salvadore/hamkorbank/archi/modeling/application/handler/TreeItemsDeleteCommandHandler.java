package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteTreeItemsCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/**
 * Групповое удаление — одна транзакция: часть не проходит — не применяется ничего. Порядок —
 * связи, представления, элементы, папки: так удаление элемента вместе с его связями проходит
 * одной командой, а связь, оставшаяся вне команды, даёт {@code 409} с перечнем.
 */
public final class TreeItemsDeleteCommandHandler {

    private final ModelingKernel kernel;
    private final ModelContents contents;

    public TreeItemsDeleteCommandHandler(ModelingKernel kernel, ModelContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Неизвестный в модели ключ — «не найдено», и не удаляется ничего. */
    public void handle(EditorIdentity actor, DeleteTreeItemsCommand command) {
        kernel.run(Operation.REORGANIZE_TREE, actor, () -> kernel.unitOfWork().write(() -> {
            ArchitectureModel model = kernel.writableModel(ModelId.of(command.modelId()), actor);
            List<UUID> folders = new ArrayList<>();
            List<UUID> elementIds = new ArrayList<>();
            for (UUID id : command.itemIds()) {
                if (model.relationships().contains(RelationshipId.of(id))) {
                    contents.removeRelationship(model, RelationshipId.of(id));
                } else if (model.view(ViewId.of(id)).isPresent()) {
                    model.forgetView(ViewId.of(id), kernel.now());
                    kernel.views().delete(ViewId.of(id));
                } else if (model.elements().contains(ElementId.of(id))) {
                    elementIds.add(id);
                } else if (model.folders().contains(FolderId.of(id))) {
                    folders.add(id);
                } else {
                    throw ModelingDomainException.notFound(Message.of(ModelingMessages.OBJECT_IN_MODEL, id));
                }
            }
            elementIds.forEach(id -> contents.removeElement(model, ElementId.of(id)));
            folders.forEach(id -> model.removeFolder(FolderId.of(id), kernel.now()));
            kernel.save(model);
            return null;
        }));
    }
}
