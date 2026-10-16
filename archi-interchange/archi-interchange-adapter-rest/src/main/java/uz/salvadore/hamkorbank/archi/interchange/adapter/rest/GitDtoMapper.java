package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitAuthorDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitBindingDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitBindingSettingsDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitChangeCountDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitChangeDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeAddedNodeDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeCascadeDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeConflictDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeMarkDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergePreviewDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeRequestDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeResultDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeViewDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitPullPreviewDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitPullRequestDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitPullResultDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ModelGitStatusDto;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitBindingSettings;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitImportService;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitMergeService;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublishingService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;

/** Git: тело запроса — в запрос сценария, результат — в DTO. */
final class GitDtoMapper {

    private GitDtoMapper() {
    }

    static GitBindingSettings settings(GitBindingSettingsDto dto) {
        List<GitBindingSettings.Author> authors = Optional.ofNullable(dto.authors()).orElse(List.of()).stream()
                .map(a -> new GitBindingSettings.Author(a.subject(), a.name(), a.email())).toList();
        return new GitBindingSettings(dto.repoUrl(), dto.branch(), dto.tokenRef(), authors);
    }

    static GitBindingDto binding(GitBinding binding) {
        return new GitBindingDto(binding.repoUrl(), binding.branch(), binding.tokenRef().value(),
                binding.status().name(), binding.lastCommitSha() == null ? null : binding.lastCommitSha().value(),
                binding.lastCheckedAt(),
                binding.authors().stream().map(a -> new GitAuthorDto(a.subject(), a.name(), a.email())).toList());
    }

    static GitImportService.PullRequest pullRequest(GitPullRequestDto dto) {
        return new GitImportService.PullRequest(dto.ref(), dto.commitSha(), dto.baseHash(), dto.comment());
    }

    static GitPullPreviewDto preview(GitImportService.Preview preview, TextCatalog text) {
        return new GitPullPreviewDto(preview.model().value(), preview.ref().ref().value(),
                preview.ref().kind().name(), preview.ref().sha().value(), preview.baseVersionNo(),
                preview.baseHash(), preview.targetHash(), preview.unchanged(), preview.applicable(),
                preview.changes().summary().stream().map(c -> new GitChangeCountDto(c.kind().name(), c.added(),
                        c.removed(), c.changed())).toList(),
                preview.changes().changes().stream().map(c -> new GitChangeDto(c.kind().name(), c.type().name(),
                        c.archiId().value(), c.archiType(), c.name(), c.fields())).toList(),
                FindingMapper.findings(preview.findings(), text));
    }

    static GitMergeService.MergeRequest mergeRequest(GitMergeRequestDto dto) {
        return new GitMergeService.MergeRequest(dto.ref(), dto.commitSha(), dto.mergeBaseSha(), dto.baseHash(),
                resolutions(dto.resolutions()), dto.comment());
    }

    static Map<String, String> resolutions(Map<String, String> resolutions) {
        return Optional.ofNullable(resolutions).orElse(Map.of());
    }

    static GitMergePreviewDto mergePreview(GitMergeService.Preview preview, TextCatalog text) {
        return new GitMergePreviewDto(preview.model().value(), preview.ref().ref().value(),
                preview.ref().kind().name(), preview.ref().sha().value(), preview.mergeBase().value(),
                preview.baseVersionNo(), preview.baseHash(), preview.targetHash(), preview.unchanged(),
                preview.applicable(),
                preview.conflicts().stream().map(c -> new GitMergeConflictDto(c.id(), c.kind().name(),
                        c.archiId().value(), c.archiType(), c.name(), c.field(), c.base(), c.ours(), c.theirs(),
                        c.resolution() == null ? null : c.resolution().name()))
                        .toList(),
                preview.cascade().stream().map(c -> new GitMergeCascadeDto(c.kind().name(), c.archiId().value(),
                        c.name(), c.reason().name(), c.view() == null ? null : c.view().value())).toList(),
                preview.changes().summary().stream().map(c -> new GitChangeCountDto(c.kind().name(), c.added(),
                        c.removed(), c.changed())).toList(),
                preview.changes().changes().stream().map(c -> new GitChangeDto(c.kind().name(), c.type().name(),
                        c.archiId().value(), c.archiType(), c.name(), c.fields())).toList(),
                preview.marks().stream().map(m -> new GitMergeMarkDto(m.archiId().value(), m.mark().name())).toList(),
                preview.views().stream().map(v -> new GitMergeViewDto(v.view().value(), v.nodes().stream()
                        .map(n -> new GitMergeAddedNodeDto(n.archiId().value(),
                                n.element() == null ? null : n.element().value(), n.type(), n.name(),
                                n.x(), n.y(), n.width(), n.height())).toList())).toList(),
                FindingMapper.findings(preview.findings(), text));
    }

    static GitMergeResultDto merged(GitMergeService.Merged merged, TextCatalog text) {
        return new GitMergeResultDto(merged.session().value(), ImportStatus.APPLIED.name(), merged.model().value(),
                merged.versionNo(), merged.created(), merged.commit().value(),
                FindingMapper.findings(merged.findings(), text));
    }

    static GitPullResultDto pulled(GitImportService.Pulled pulled, TextCatalog text) {
        return new GitPullResultDto(pulled.session().value(), ImportStatus.APPLIED.name(), pulled.model().value(),
                pulled.versionNo(), pulled.created(), pulled.commit().value(),
                FindingMapper.findings(pulled.findings(), text));
    }

    static ModelGitStatusDto status(GitPublishingService.ModelGitStatus status) {
        return new ModelGitStatusDto(status.model().value(), status.state().name(), status.pending(),
                status.last() == null ? null : status.last().versionNo(),
                status.last() == null ? null : status.last().sha().value(), status.divergedVersionNo());
    }
}
