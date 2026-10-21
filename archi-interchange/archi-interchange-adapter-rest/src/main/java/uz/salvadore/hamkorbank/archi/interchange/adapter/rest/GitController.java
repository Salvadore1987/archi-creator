package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitBindingDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitBindingSettingsDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergePreviewDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergePreviewRequestDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeRequestDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeResultDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitPullPreviewDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitPullPreviewRequestDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitPullRequestDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitPullResultDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ModelGitStatusDto;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.DisableGitBindingCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.PushModelToGitCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetGitBindingQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.PreviewGitPullQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitBindingApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitMergeApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitPublishingApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitPullApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Привязка рабочего пространства к Git-репозиторию, публикация версий, импорт состояния
 * по ссылке и слияние предложения по сущностям.
 */
@RestController
@RequestMapping("/api/v1")
public class GitController {

    private final GitBindingApplicationService bindings;
    private final GitPublishingApplicationService publishing;
    private final GitPullApplicationService pulls;
    private final GitMergeApplicationService merging;

    public GitController(GitBindingApplicationService bindings, GitPublishingApplicationService publishing,
                         GitPullApplicationService pulls, GitMergeApplicationService merging) {
        this.bindings = bindings;
        this.publishing = publishing;
        this.pulls = pulls;
        this.merging = merging;
    }

    /** Отчёт об изменениях по ссылке — ничего не меняет. */
    @PostMapping("/models/{id}/git/pull/preview")
    public GitPullPreviewDto previewPull(EditorIdentity actor, @PathVariable UUID id,
                                         @Valid @RequestBody GitPullPreviewRequestDto body) {
        return GitDtoMapper.preview(pulls.previewGitPull(actor, new PreviewGitPullQuery(id, body.ref())));
    }

    /** Применить коммит из отчёта новой версией модели. */
    @PostMapping("/models/{id}/git/pull")
    public GitPullResultDto pull(EditorIdentity actor, @PathVariable UUID id,
                                 @Valid @RequestBody GitPullRequestDto body) {
        return GitDtoMapper.pulled(pulls.pullModelFromGit(actor, GitDtoMapper.pullRequest(id, body)));
    }

    /** Отчёт слияния предложения по сущностям с выборами по конфликтам — ничего не меняет. */
    @PostMapping("/models/{id}/git/merge/preview")
    public GitMergePreviewDto previewMerge(EditorIdentity actor, @PathVariable UUID id,
                                           @Valid @RequestBody GitMergePreviewRequestDto body) {
        return GitDtoMapper.mergePreview(merging.previewGitMerge(actor, GitDtoMapper.mergePreviewRequest(id, body)));
    }

    /** Применить слияние из отчёта новой версией модели. */
    @PostMapping("/models/{id}/git/merge")
    public GitMergeResultDto merge(EditorIdentity actor, @PathVariable UUID id,
                                   @Valid @RequestBody GitMergeRequestDto body) {
        return GitDtoMapper.merged(merging.mergeModelBranch(actor, GitDtoMapper.mergeRequest(id, body)));
    }

    /** Опубликовать ожидающие версии модели сейчас; ответ — состояние модели в Git. */
    @PostMapping("/models/{id}/git/push")
    public ModelGitStatusDto push(EditorIdentity actor, @PathVariable UUID id) {
        return GitDtoMapper.status(publishing.pushModelToGit(actor, new PushModelToGitCommand(id)));
    }

    /** Текущая привязка рабочего пространства к репозиторию. */
    @GetMapping("/workspaces/{id}/git-binding")
    public GitBindingDto binding(EditorIdentity actor, @PathVariable UUID id) {
        return GitDtoMapper.binding(bindings.getGitBinding(actor, new GetGitBindingQuery(id)));
    }

    /** Задаёт привязку пространства заново: адрес, ветку, ссылку на токен и авторов. */
    @PutMapping("/workspaces/{id}/git-binding")
    public GitBindingDto configure(EditorIdentity actor, @PathVariable UUID id,
                                   @Valid @RequestBody GitBindingSettingsDto settings) {
        return GitDtoMapper.binding(bindings.configureGitBinding(actor, GitDtoMapper.settings(id, settings)));
    }

    /** Снимает привязку пространства к репозиторию; ответ без тела. */
    @DeleteMapping("/workspaces/{id}/git-binding")
    public ResponseEntity<Void> disable(EditorIdentity actor, @PathVariable UUID id) {
        bindings.disableGitBinding(actor, new DisableGitBindingCommand(id));
        return ResponseEntity.noContent().build();
    }
}
