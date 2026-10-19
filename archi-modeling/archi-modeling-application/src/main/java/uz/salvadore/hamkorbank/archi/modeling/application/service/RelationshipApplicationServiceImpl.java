package uz.salvadore.hamkorbank.archi.modeling.application.service;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CreateRelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationshipCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationshipDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationshipEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.RelationshipApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Связи модели: делегирует в handler'ы сценариев. */
public final class RelationshipApplicationServiceImpl implements RelationshipApplicationService {

    private final RelationshipCreateCommandHandler create;
    private final RelationshipEditCommandHandler edit;
    private final RelationshipDeleteCommandHandler delete;

    public RelationshipApplicationServiceImpl(RelationshipCreateCommandHandler create,
                                              RelationshipEditCommandHandler edit,
                                              RelationshipDeleteCommandHandler delete) {
        this.create = create;
        this.edit = edit;
        this.delete = delete;
    }

    @Override
    public CreateRelationshipResponse createRelationship(EditorIdentity actor, CreateRelationshipCommand command) {
        return create.handle(actor, command);
    }

    @Override
    public RelationshipResponse editRelationship(EditorIdentity actor, EditRelationshipCommand command) {
        return edit.handle(actor, command);
    }

    @Override
    public void deleteRelationship(EditorIdentity actor, DeleteRelationshipCommand command) {
        delete.handle(actor, command);
    }
}
