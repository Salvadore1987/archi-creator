package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.MoveTreeItemsCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;

/** Перенос объектов дерева в папку; соседи не перенумеровываются, корни не меняются. */
public final class TreeItemsMoveCommandHandler {

    private final ModelingKernel kernel;

    public TreeItemsMoveCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Все объекты переносятся одной транзакцией; представление узнаёт новую папку вместе с моделью. */
    public void handle(EditorIdentity actor, MoveTreeItemsCommand command) {
        kernel.run(Operation.REORGANIZE_TREE, actor, () -> kernel.unitOfWork().write(() -> {
            ArchitectureModel model = kernel.writableModel(ModelId.of(command.modelId()), actor);
            for (ViewRef moved : model.moveToFolder(FolderId.of(command.targetFolderId()), command.itemIds(),
                    kernel.now())) {
                View view = kernel.views().load(moved.id()).orElseThrow();
                view.moveTo(moved);
                kernel.views().save(view);
            }
            kernel.save(model);
            return null;
        }));
    }
}
