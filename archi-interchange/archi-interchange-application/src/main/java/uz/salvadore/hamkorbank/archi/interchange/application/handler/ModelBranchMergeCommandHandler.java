package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.MergeModelBranchCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitAppliedResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitMerge;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.GitDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Применение слияния из отчёта новой версией модели. */
public final class ModelBranchMergeCommandHandler {

    private final InterchangeKernel kernel;
    private final GitMerge merge;

    public ModelBranchMergeCommandHandler(InterchangeKernel kernel, GitMerge merge) {
        this.kernel = kernel;
        this.merge = merge;
    }

    /** Итог слияния; отказы — как у шагов {@link GitMerge#apply}. */
    public GitAppliedResponse handle(EditorIdentity actor, MergeModelBranchCommand command) {
        return kernel.run(InterchangeOperation.MERGE_MODEL_BRANCH, actor, () -> GitDataMapper.merged(
                merge.apply(actor, new ModelId(command.modelId()), new GitMerge.MergeRequest(command.ref(),
                        command.commitSha(), command.mergeBaseSha(), command.baseHash(), command.resolutions(),
                        command.comment())), kernel.texts()));
    }
}
