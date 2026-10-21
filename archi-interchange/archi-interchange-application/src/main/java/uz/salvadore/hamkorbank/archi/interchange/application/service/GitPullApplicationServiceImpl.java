package uz.salvadore.hamkorbank.archi.interchange.application.service;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.PullModelFromGitCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.PreviewGitPullQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitAppliedResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitPullPreviewResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelGitPullCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelGitPullPreviewQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitPullApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Импорт по ссылке Git: делегирует в handler'ы сценариев. */
public final class GitPullApplicationServiceImpl implements GitPullApplicationService {

    private final ModelGitPullPreviewQueryHandler preview;
    private final ModelGitPullCommandHandler pull;

    public GitPullApplicationServiceImpl(ModelGitPullPreviewQueryHandler preview, ModelGitPullCommandHandler pull) {
        this.preview = preview;
        this.pull = pull;
    }

    @Override
    public GitPullPreviewResponse previewGitPull(EditorIdentity actor, PreviewGitPullQuery query) {
        return preview.handle(actor, query);
    }

    @Override
    public GitAppliedResponse pullModelFromGit(EditorIdentity actor, PullModelFromGitCommand command) {
        return pull.handle(actor, command);
    }
}
