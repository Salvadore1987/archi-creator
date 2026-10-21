package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.PushModelToGitCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ModelGitStatusResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublisher;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.GitDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Опубликовать очередь модели сейчас — после восстановления сети или после того, как расхождение
 * разобрали руками. Версию не создаёт.
 */
public final class ModelGitPushCommandHandler {

    private final InterchangeKernel kernel;
    private final GitPublisher publisher;

    public ModelGitPushCommandHandler(InterchangeKernel kernel, GitPublisher publisher) {
        this.kernel = kernel;
        this.publisher = publisher;
    }

    /** Состояние модели в Git после попытки. */
    public ModelGitStatusResponse handle(EditorIdentity actor, PushModelToGitCommand command) {
        return kernel.run(InterchangeOperation.PUSH_MODEL_TO_GIT, actor,
                () -> GitDataMapper.status(publisher.push(actor, new ModelId(command.modelId()))));
    }
}
