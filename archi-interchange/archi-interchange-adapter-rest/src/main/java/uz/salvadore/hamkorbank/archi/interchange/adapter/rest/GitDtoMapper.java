package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergePreviewRequestDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeRequestDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeResultDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitMergeViewDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitPullPreviewDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitPullRequestDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitPullResultDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ModelGitStatusDto;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureGitBindingCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.MergeModelBranchCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.PullModelFromGitCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.PreviewGitMergeQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitAppliedResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitBindingResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitChangeCountResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitChangeResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitMergePreviewResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitPullPreviewResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ModelGitStatusResponse;

/** Git: тело запроса — в команду или запрос сценария, результат — в DTO. */
final class GitDtoMapper {

    private GitDtoMapper() {
    }

    static ConfigureGitBindingCommand settings(UUID workspaceId, GitBindingSettingsDto dto) {
        List<ConfigureGitBindingCommand.Author> authors = Optional.ofNullable(dto.authors()).orElse(List.of())
                .stream().map(a -> new ConfigureGitBindingCommand.Author(a.subject(), a.name(), a.email())).toList();
        return new ConfigureGitBindingCommand(workspaceId, dto.repoUrl(), dto.branch(), dto.tokenRef(), authors);
    }

    static GitBindingDto binding(GitBindingResponse binding) {
        return new GitBindingDto(binding.repoUrl(), binding.branch(), binding.tokenRef(), binding.status(),
                binding.lastCommitSha(), binding.lastCheckedAt(),
                binding.authors().stream().map(a -> new GitAuthorDto(a.subject(), a.name(), a.email())).toList());
    }

    static PullModelFromGitCommand pullRequest(UUID modelId, GitPullRequestDto dto) {
        return new PullModelFromGitCommand(modelId, dto.ref(), dto.commitSha(), dto.baseHash(), dto.comment());
    }

    static GitPullPreviewDto preview(GitPullPreviewResponse preview) {
        return new GitPullPreviewDto(preview.modelId(), preview.ref(), preview.refKind(), preview.commitSha(),
                preview.baseVersionNo(), preview.baseHash(), preview.targetHash(), preview.unchanged(),
                preview.applicable(), summary(preview.summary()), changes(preview.changes()),
                FindingMapper.findings(preview.findings()));
    }

    static PreviewGitMergeQuery mergePreviewRequest(UUID modelId, GitMergePreviewRequestDto dto) {
        return new PreviewGitMergeQuery(modelId, dto.ref(), Optional.ofNullable(dto.resolutions()).orElse(Map.of()));
    }

    static MergeModelBranchCommand mergeRequest(UUID modelId, GitMergeRequestDto dto) {
        return new MergeModelBranchCommand(modelId, dto.ref(), dto.commitSha(), dto.mergeBaseSha(), dto.baseHash(),
                Optional.ofNullable(dto.resolutions()).orElse(Map.of()), dto.comment());
    }

    static GitMergePreviewDto mergePreview(GitMergePreviewResponse preview) {
        return new GitMergePreviewDto(preview.modelId(), preview.ref(), preview.refKind(), preview.commitSha(),
                preview.mergeBaseSha(), preview.baseVersionNo(), preview.baseHash(), preview.targetHash(),
                preview.unchanged(), preview.applicable(),
                preview.conflicts().stream().map(c -> new GitMergeConflictDto(c.id(), c.kind(), c.archiId(),
                        c.type(), c.name(), c.field(), c.base(), c.ours(), c.theirs(), c.resolution())).toList(),
                preview.cascade().stream().map(c -> new GitMergeCascadeDto(c.kind(), c.archiId(), c.name(),
                        c.reason(), c.viewArchiId())).toList(),
                summary(preview.summary()), changes(preview.changes()),
                preview.marks().stream().map(m -> new GitMergeMarkDto(m.archiId(), m.mark())).toList(),
                preview.views().stream().map(v -> new GitMergeViewDto(v.archiId(), v.addedNodes().stream()
                        .map(n -> new GitMergeAddedNodeDto(n.archiId(), n.elementArchiId(), n.type(), n.name(),
                                n.x(), n.y(), n.width(), n.height())).toList())).toList(),
                FindingMapper.findings(preview.findings()));
    }

    static GitMergeResultDto merged(GitAppliedResponse merged) {
        return new GitMergeResultDto(merged.sessionId(), merged.status(), merged.modelId(), merged.versionNo(),
                merged.created(), merged.commitSha(), FindingMapper.findings(merged.findings()));
    }

    static GitPullResultDto pulled(GitAppliedResponse pulled) {
        return new GitPullResultDto(pulled.sessionId(), pulled.status(), pulled.modelId(), pulled.versionNo(),
                pulled.created(), pulled.commitSha(), FindingMapper.findings(pulled.findings()));
    }

    static ModelGitStatusDto status(ModelGitStatusResponse status) {
        return new ModelGitStatusDto(status.modelId(), status.state(), status.pending(), status.versionNo(),
                status.gitSha(), status.divergedVersionNo());
    }

    private static List<GitChangeCountDto> summary(List<GitChangeCountResponse> summary) {
        return summary.stream().map(c -> new GitChangeCountDto(c.kind(), c.added(), c.removed(), c.changed()))
                .toList();
    }

    private static List<GitChangeDto> changes(List<GitChangeResponse> changes) {
        return changes.stream().map(c -> new GitChangeDto(c.kind(), c.change(), c.archiId(), c.type(), c.name(),
                c.fields())).toList();
    }
}
