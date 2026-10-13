package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitAuthorDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitBindingDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitBindingSettingsDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ModelGitStatusDto;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitBindingSettings;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublishingService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;

/** Привязка к Git: тело запроса — в настройку сценария, результат — в DTO. */
final class GitDtoMapper {

    private GitDtoMapper() {
    }

    static GitBindingSettings settings(GitBindingSettingsDto dto) {
        List<GitBindingSettings.Author> authors = Optional.ofNullable(dto.authors()).orElse(List.of()).stream()
                .map(a -> new GitBindingSettings.Author(a.subject(), a.name(), a.email())).toList();
        return new GitBindingSettings(dto.repoUrl(), Optional.ofNullable(dto.branch()), dto.tokenRef(), authors);
    }

    static GitBindingDto binding(GitBinding binding) {
        return new GitBindingDto(binding.repoUrl(), binding.branch(), binding.tokenRef().value(),
                binding.status().name(), binding.lastCommitSha().map(GitSha::value).orElse(null),
                binding.lastCheckedAt().orElse(null),
                binding.authors().stream().map(a -> new GitAuthorDto(a.subject(), a.name(), a.email())).toList());
    }

    static ModelGitStatusDto status(GitPublishingService.ModelGitStatus status) {
        return new ModelGitStatusDto(status.model().value(), status.state().name(), status.pending(),
                status.last().map(PublishedVersions.Published::versionNo).orElse(null),
                status.last().map(p -> p.sha().value()).orElse(null), status.divergedVersionNo().orElse(null));
    }
}
