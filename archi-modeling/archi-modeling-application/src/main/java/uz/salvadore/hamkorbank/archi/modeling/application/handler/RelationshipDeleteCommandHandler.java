package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;

/** Удаление связи снимает её рёбра на представлениях; примыкающая связь — {@code 409}. */
public final class RelationshipDeleteCommandHandler {

    private final ModelingKernel kernel;
    private final ModelContents contents;

    public RelationshipDeleteCommandHandler(ModelingKernel kernel, ModelContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Связь удаляется под блокировкой модели. */
    public void handle(EditorIdentity actor, DeleteRelationshipCommand command) {
        kernel.run(Operation.DELETE_RELATIONSHIP, actor, () -> kernel.unitOfWork().write(() -> {
            ArchitectureModel model = kernel.writableModel(contents.ownerOf(command.relationshipId()), actor);
            contents.removeRelationship(model, RelationshipId.of(command.relationshipId()));
            kernel.save(model);
            return null;
        }));
    }
}
