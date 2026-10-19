package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RenameTreeItemCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/** Переименование папки, элемента, связи или представления в дереве. */
public final class TreeItemRenameCommandHandler {

    private final ModelingKernel kernel;

    public TreeItemRenameCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Объект ищется среди папок, элементов, связей и представлений модели; не нашёлся — «не найдено». */
    public void handle(EditorIdentity actor, RenameTreeItemCommand command) {
        kernel.run(Operation.REORGANIZE_TREE, actor, () -> kernel.unitOfWork().write(() -> {
            ArchitectureModel model = kernel.writableModel(ModelId.of(command.modelId()), actor);
            UUID itemId = command.itemId();
            String name = command.name();
            if (model.folders().contains(FolderId.of(itemId))) {
                model.renameFolder(FolderId.of(itemId), name, kernel.now());
            } else if (model.elements().contains(ElementId.of(itemId))) {
                Element element = model.requireElement(ElementId.of(itemId));
                model.updateElement(element.id(), name, element.documentation(), element.properties(), kernel.now());
            } else if (model.relationships().contains(RelationshipId.of(itemId))) {
                Relationship relationship = model.requireRelationship(RelationshipId.of(itemId));
                model.updateRelationship(relationship.id(), name, relationship.documentation(),
                        relationship.properties(), kernel.now());
            } else if (model.view(ViewId.of(itemId)).isPresent()) {
                View view = kernel.views().load(ViewId.of(itemId)).orElseThrow();
                view.rename(name);
                model.renameView(view.id(), name, kernel.now());
                kernel.views().save(view);
            } else {
                throw ModelingDomainException.notFound(Message.of(ModelingMessages.OBJECT_IN_MODEL, itemId));
            }
            kernel.save(model);
            return null;
        }));
    }
}
