package uz.salvadore.hamkorbank.archi.interchange.application.port.in;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.MergeModelBranchCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.PreviewGitMergeQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitAppliedResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitMergePreviewResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Слияние предложения из Git по сущностям: отчёт с выборами, затем применение. */
public interface GitMergeApplicationService {

    /** Отчёт слияния с выборами по конфликтам; ничего не меняет. */
    GitMergePreviewResponse previewGitMerge(EditorIdentity actor, PreviewGitMergeQuery query);

    /** Применить слияние из отчёта новой версией модели. */
    GitAppliedResponse mergeModelBranch(EditorIdentity actor, MergeModelBranchCommand command);
}
