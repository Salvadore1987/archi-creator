package uz.salvadore.hamkorbank.archi.interchange.application.port.in;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.PullModelFromGitCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.PreviewGitPullQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitAppliedResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitPullPreviewResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Импорт состояния модели по ссылке Git: отчёт, затем применение того, что отчёт показал. */
public interface GitPullApplicationService {

    /** Отчёт об изменениях; ничего не меняет. */
    GitPullPreviewResponse previewGitPull(EditorIdentity actor, PreviewGitPullQuery query);

    /** Применить коммит из отчёта новой версией модели. */
    GitAppliedResponse pullModelFromGit(EditorIdentity actor, PullModelFromGitCommand command);
}
