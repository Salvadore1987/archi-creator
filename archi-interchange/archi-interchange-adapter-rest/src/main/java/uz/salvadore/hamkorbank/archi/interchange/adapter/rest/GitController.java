package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import java.util.Optional;
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
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitBindingService;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitImportService;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitMergeService;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublishingService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Привязка рабочего пространства к Git-репозиторию, публикация версий, импорт состояния
 * по ссылке и слияние предложения по сущностям.
 */
@RestController
@RequestMapping("/api/v1")
public class GitController {

    private final GitBindingService bindings;
    private final GitPublishingService publishing;
    private final GitImportService imports;
    private final GitMergeService merging;
    private final TextCatalog text;

    public GitController(GitBindingService bindings, GitPublishingService publishing, GitImportService imports,
                         GitMergeService merging, TextCatalog text) {
        this.bindings = bindings;
        this.publishing = publishing;
        this.imports = imports;
        this.merging = merging;
        this.text = text;
    }

    /** Отчёт об изменениях по ссылке — ничего не меняет. */
    @PostMapping("/models/{id}/git/pull/preview")
    public GitPullPreviewDto previewPull(EditorIdentity actor, @PathVariable UUID id,
                                         @RequestBody GitPullPreviewRequestDto body) {
        return GitDtoMapper.preview(imports.preview(actor, new ModelId(id), Optional.ofNullable(body.ref())), text);
    }

    /** Применить коммит из отчёта новой версией модели. */
    @PostMapping("/models/{id}/git/pull")
    public GitPullResultDto pull(EditorIdentity actor, @PathVariable UUID id, @RequestBody GitPullRequestDto body) {
        return GitDtoMapper.pulled(imports.apply(actor, new ModelId(id), GitDtoMapper.pullRequest(body)), text);
    }

    /** Отчёт слияния предложения по сущностям с выборами по конфликтам — ничего не меняет. */
    @PostMapping("/models/{id}/git/merge/preview")
    public GitMergePreviewDto previewMerge(EditorIdentity actor, @PathVariable UUID id,
                                           @RequestBody GitMergePreviewRequestDto body) {
        return GitDtoMapper.mergePreview(merging.preview(actor, new ModelId(id), Optional.ofNullable(body.ref()),
                GitDtoMapper.resolutions(body.resolutions())), text);
    }

    /** Применить слияние из отчёта новой версией модели. */
    @PostMapping("/models/{id}/git/merge")
    public GitMergeResultDto merge(EditorIdentity actor, @PathVariable UUID id, @RequestBody GitMergeRequestDto body) {
        return GitDtoMapper.merged(merging.apply(actor, new ModelId(id), GitDtoMapper.mergeRequest(body)), text);
    }

    /** Опубликовать ожидающие версии модели сейчас; ответ — состояние модели в Git. */
    @PostMapping("/models/{id}/git/push")
    public ModelGitStatusDto push(EditorIdentity actor, @PathVariable UUID id) {
        return GitDtoMapper.status(publishing.push(actor, new ModelId(id)));
    }

    /** Текущая привязка рабочего пространства к репозиторию. */
    @GetMapping("/workspaces/{id}/git-binding")
    public GitBindingDto binding(EditorIdentity actor, @PathVariable UUID id) {
        return GitDtoMapper.binding(bindings.binding(actor, id));
    }

    /** Задаёт привязку пространства заново: адрес, ветку, ссылку на токен и авторов. */
    @PutMapping("/workspaces/{id}/git-binding")
    public GitBindingDto configure(EditorIdentity actor, @PathVariable UUID id,
                                   @RequestBody GitBindingSettingsDto settings) {
        return GitDtoMapper.binding(bindings.configure(actor, id, GitDtoMapper.settings(settings)));
    }

    /** Снимает привязку пространства к репозиторию; ответ без тела. */
    @DeleteMapping("/workspaces/{id}/git-binding")
    public ResponseEntity<Void> disable(EditorIdentity actor, @PathVariable UUID id) {
        bindings.disable(actor, id);
        return ResponseEntity.noContent().build();
    }
}
