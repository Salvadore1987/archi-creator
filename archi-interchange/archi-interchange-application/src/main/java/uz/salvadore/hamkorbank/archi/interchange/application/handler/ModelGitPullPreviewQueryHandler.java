package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.PreviewGitPullQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitPullPreviewResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPull;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.GitDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Отчёт об изменениях по ссылке; в базе ничего не меняется. */
public final class ModelGitPullPreviewQueryHandler {

    private final InterchangeKernel kernel;
    private final GitPull pull;

    public ModelGitPullPreviewQueryHandler(InterchangeKernel kernel, GitPull pull) {
        this.kernel = kernel;
        this.pull = pull;
    }

    /** Отчёт с находками на языке запроса. */
    public GitPullPreviewResponse handle(EditorIdentity actor, PreviewGitPullQuery query) {
        return kernel.run(InterchangeOperation.PULL_MODEL_FROM_GIT, actor, () -> GitDataMapper.pullPreview(
                pull.preview(actor, new ModelId(query.modelId()), query.ref()), kernel.texts()));
    }
}
