package uz.salvadore.hamkorbank.archi.modeling.application.service;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PurgeModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RestoreModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelPurgeCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelRestoreCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Жизненный цикл модели: делегирует в handler'ы сценариев. */
public final class ModelApplicationServiceImpl implements ModelApplicationService {

    private final ModelCreateCommandHandler create;
    private final ModelEditCommandHandler edit;
    private final ModelDeleteCommandHandler delete;
    private final ModelRestoreCommandHandler restore;
    private final ModelPurgeCommandHandler purge;

    public ModelApplicationServiceImpl(ModelCreateCommandHandler create, ModelEditCommandHandler edit,
                                       ModelDeleteCommandHandler delete, ModelRestoreCommandHandler restore,
                                       ModelPurgeCommandHandler purge) {
        this.create = create;
        this.edit = edit;
        this.delete = delete;
        this.restore = restore;
        this.purge = purge;
    }

    @Override
    public ModelSummaryResponse createModel(EditorIdentity actor, CreateModelCommand command) {
        return create.handle(actor, command);
    }

    @Override
    public ModelSummaryResponse editModel(EditorIdentity actor, EditModelCommand command) {
        return edit.handle(actor, command);
    }

    @Override
    public void deleteModel(EditorIdentity actor, DeleteModelCommand command) {
        delete.handle(actor, command);
    }

    @Override
    public ModelSummaryResponse restoreModel(EditorIdentity actor, RestoreModelCommand command) {
        return restore.handle(actor, command);
    }

    @Override
    public void purgeModel(EditorIdentity actor, PurgeModelCommand command) {
        purge.handle(actor, command);
    }
}
