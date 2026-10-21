package uz.salvadore.hamkorbank.archi.interchange.application.service;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.MergeModelBranchCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.PreviewGitMergeQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitAppliedResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitMergePreviewResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelBranchMergeCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelBranchMergePreviewQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitMergeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Слияние из Git: делегирует в handler'ы сценариев. */
public final class GitMergeApplicationServiceImpl implements GitMergeApplicationService {

    private final ModelBranchMergePreviewQueryHandler preview;
    private final ModelBranchMergeCommandHandler merge;

    public GitMergeApplicationServiceImpl(ModelBranchMergePreviewQueryHandler preview,
                                          ModelBranchMergeCommandHandler merge) {
        this.preview = preview;
        this.merge = merge;
    }

    @Override
    public GitMergePreviewResponse previewGitMerge(EditorIdentity actor, PreviewGitMergeQuery query) {
        return preview.handle(actor, query);
    }

    @Override
    public GitAppliedResponse mergeModelBranch(EditorIdentity actor, MergeModelBranchCommand command) {
        return merge.handle(actor, command);
    }
}
