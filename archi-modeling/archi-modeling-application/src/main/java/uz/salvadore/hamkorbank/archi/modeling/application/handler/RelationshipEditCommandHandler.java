package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;

/** Правка имени, описания и свойств связи; нужна блокировка модели. */
public final class RelationshipEditCommandHandler {

    private final ModelingKernel kernel;
    private final ModelContents contents;

    public RelationshipEditCommandHandler(ModelingKernel kernel, ModelContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Заданные поля меняются, незаданные остаются. */
    public RelationshipResponse handle(EditorIdentity actor, EditRelationshipCommand command) {
        return kernel.run(Operation.UPDATE_RELATIONSHIP, actor, () -> kernel.unitOfWork().write(() -> {
            RelationshipId relationshipId = RelationshipId.of(command.relationshipId());
            ArchitectureModel model = kernel.writableModel(contents.ownerOf(command.relationshipId()), actor);
            Relationship current = model.requireRelationship(relationshipId);
            Relationship updated = model.updateRelationship(relationshipId,
                    command.name() != null ? command.name() : current.name(),
                    command.documentation() != null ? command.documentation() : current.documentation(),
                    command.properties() != null ? ModelContents.numbered(command.properties()) : current.properties(),
                    kernel.now());
            kernel.save(model);
            return ModelDataMapper.relationship(updated);
        }));
    }
}
