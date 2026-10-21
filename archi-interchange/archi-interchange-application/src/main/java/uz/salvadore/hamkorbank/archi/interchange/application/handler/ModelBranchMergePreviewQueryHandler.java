package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.PreviewGitMergeQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitMergePreviewResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitMerge;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.GitDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Отчёт слияния предложения по сущностям с выборами по конфликтам; ничего не меняет. */
public final class ModelBranchMergePreviewQueryHandler {

    private final InterchangeKernel kernel;
    private final GitMerge merge;

    public ModelBranchMergePreviewQueryHandler(InterchangeKernel kernel, GitMerge merge) {
        this.kernel = kernel;
        this.merge = merge;
    }

    /** Отчёт с находками на языке запроса. */
    public GitMergePreviewResponse handle(EditorIdentity actor, PreviewGitMergeQuery query) {
        return kernel.run(InterchangeOperation.MERGE_MODEL_BRANCH, actor, () -> GitDataMapper.mergePreview(
                merge.preview(actor, new ModelId(query.modelId()), query.ref(), query.resolutions()),
                kernel.texts()));
    }
}
