package uz.salvadore.hamkorbank.archi.modeling.application.service;

import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionCommitter;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Входящий порт для interchange: применить импортированный документ как новую модель
 * (порт {@code ModelWriter}). Роль проверяет вызывающий — у импорта своя строка в таблице доступа.
 */
public final class ModelImportService {

    private final ModelingKernel kernel;
    private final WorkspaceRepository workspaces;
    private final VersionCommitter committer;

    public ModelImportService(ModelingKernel kernel, WorkspaceRepository workspaces, VersionCommitter committer) {
        this.kernel = kernel;
        this.workspaces = workspaces;
        this.committer = committer;
    }

    /** Модель и представления пишутся одной транзакцией; версия 1 — снимок, собранный из базы. */
    public ModelVersion store(ModelContent content, EditorIdentity actor, String comment) {
        return kernel.unitOfWork.write(() -> {
            workspaces.find(content.model().workspaceId()).orElseThrow(
                    () -> ModelingDomainException.notFound(
                            Message.of(ModelingMessages.WORKSPACE, content.model().workspaceId())));
            kernel.save(content.model());
            content.views().forEach(kernel.views::save);
            return committer.commit(content.model(), actor, comment);
        });
    }
}
