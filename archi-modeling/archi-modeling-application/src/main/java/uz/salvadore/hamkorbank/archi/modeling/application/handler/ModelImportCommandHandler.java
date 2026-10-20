package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.time.Instant;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ImportModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ImportModelResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelRowsMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Импортированный документ становится новой моделью: агрегаты собираются из строк, модель
 * и представления пишутся одной транзакцией, версия 1 — снимок, собранный из базы. Роль проверяет
 * вызывающий — у импорта своя строка в таблице доступа.
 */
public final class ModelImportCommandHandler {

    private final ModelingKernel kernel;
    private final WorkspaceRepository workspaces;
    private final VersionCommitter committer;

    public ModelImportCommandHandler(ModelingKernel kernel, WorkspaceRepository workspaces,
                                     VersionCommitter committer) {
        this.kernel = kernel;
        this.workspaces = workspaces;
        this.committer = committer;
    }

    /** Первая версия новой модели. */
    public ImportModelResponse handle(EditorIdentity actor, ImportModelCommand command) {
        return kernel.unitOfWork().write(() -> {
            WorkspaceId workspace = WorkspaceId.of(command.workspaceId());
            workspaces.find(workspace).orElseThrow(
                    () -> ModelingDomainException.notFound(Message.of(ModelingMessages.WORKSPACE, workspace)));
            Instant now = kernel.now();
            ModelContent content = ModelRowsMapper.content(command.rows(), new ModelRowsMapper.Identity(
                    ModelId.of(kernel.uuids().next()), workspace, actor.subject(), now, now, 0),
                    () -> FolderId.of(kernel.uuids().next()), kernel.archiIds()::next);
            kernel.save(content.model());
            content.views().forEach(kernel.views()::save);
            long versionNo = committer.commit(content.model(), actor, command.comment()).versionNo();
            return new ImportModelResponse(content.model().id().value(), versionNo, content.model().opaqueCount());
        });
    }
}
