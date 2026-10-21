package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.PullModelFromGitCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitAppliedResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPull;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.GitDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Применение коммита из отчёта новой версией модели. */
public final class ModelGitPullCommandHandler {

    private final InterchangeKernel kernel;
    private final GitPull pull;

    public ModelGitPullCommandHandler(InterchangeKernel kernel, GitPull pull) {
        this.kernel = kernel;
        this.pull = pull;
    }

    /** Итог применения; отказы — как у шагов {@link GitPull#apply}. */
    public GitAppliedResponse handle(EditorIdentity actor, PullModelFromGitCommand command) {
        return kernel.run(InterchangeOperation.PULL_MODEL_FROM_GIT, actor, () -> GitDataMapper.pulled(
                pull.apply(actor, new ModelId(command.modelId()), new GitPull.PullRequest(command.ref(),
                        command.commitSha(), command.baseHash(), command.comment())), kernel.texts()));
    }
}
