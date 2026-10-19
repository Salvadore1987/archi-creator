package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ResolveWorkspaceQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Пустая модель с девятью корневыми папками и версией 1 — точкой отсчёта истории. Повтор
 * с тем же ключом возвращает ту же модель; пространство не названо — первое.
 */
public final class ModelCreateCommandHandler {

    private final ModelingKernel kernel;
    private final WorkspaceRepository workspaces;
    private final WorkspaceResolveQueryHandler resolve;
    private final VersionCommitter versions;

    public ModelCreateCommandHandler(ModelingKernel kernel, WorkspaceRepository workspaces,
                                     WorkspaceResolveQueryHandler resolve, VersionCommitter versions) {
        this.kernel = kernel;
        this.workspaces = workspaces;
        this.resolve = resolve;
        this.versions = versions;
    }

    /** Новая модель или, при повторе ключа, та же. */
    public ModelSummaryResponse handle(EditorIdentity actor, CreateModelCommand command) {
        return kernel.run(Operation.CREATE_MODEL, actor, () -> {
            WorkspaceId workspaceId = WorkspaceId.of(
                    resolve.handle(new ResolveWorkspaceQuery(command.workspaceId())).workspaceId());
            return kernel.unitOfWork().write(() -> kernel.idempotent("CreateModel", actor, command.idempotencyKey(),
                    IdempotentCommand.fingerprint(workspaceId, command.name()),
                    () -> {
                        workspaces.find(workspaceId).orElseThrow(() -> ModelingDomainException.notFound(
                                Message.of(ModelingMessages.WORKSPACE, workspaceId)));
                        ArchitectureModel model = ArchitectureModel.create(ModelId.next(kernel.uuids()), workspaceId,
                                kernel.archiIds().next(), command.name(), actor, kernel.now(),
                                () -> FolderId.next(kernel.uuids()), kernel.archiIds()::next);
                        kernel.save(model);
                        versions.commit(model, actor,
                                kernel.texts().text(Message.of(ModelingMessages.COMMENT_MODEL_CREATED)));
                        return model.id().toString();
                    },
                    ref -> ModelDataMapper.summary(kernel.models().findHeader(ModelId.of(ref)).orElseThrow())));
        });
    }
}
