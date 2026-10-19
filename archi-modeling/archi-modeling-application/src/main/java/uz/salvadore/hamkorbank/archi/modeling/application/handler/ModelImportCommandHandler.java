package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.VersionDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Импортированный документ становится новой моделью: модель и представления пишутся одной
 * транзакцией, версия 1 — снимок, собранный из базы. Роль проверяет вызывающий — у импорта
 * своя строка в таблице доступа.
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
    public VersionResponse handle(EditorIdentity actor, ModelContent content, String comment) {
        return kernel.unitOfWork().write(() -> {
            workspaces.find(content.model().workspaceId()).orElseThrow(
                    () -> ModelingDomainException.notFound(
                            Message.of(ModelingMessages.WORKSPACE, content.model().workspaceId())));
            kernel.save(content.model());
            content.views().forEach(kernel.views()::save);
            return VersionDataMapper.version(committer.commit(content.model(), actor, comment));
        });
    }
}
