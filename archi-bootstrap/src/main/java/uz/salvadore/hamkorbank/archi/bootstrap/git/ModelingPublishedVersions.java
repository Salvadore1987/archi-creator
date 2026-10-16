package uz.salvadore.hamkorbank.archi.bootstrap.git;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Версии modeling для публикации: перевод идентификаторов двух контекстов и ничего больше. */
public final class ModelingPublishedVersions implements PublishedVersions {

    private final VersionService versions;

    public ModelingPublishedVersions(VersionService versions) {
        this.versions = versions;
    }

    @Override
    public Optional<Version> version(ModelId model, long versionNo) {
        return versions.committed(modeling(model), versionNo).map(c -> new Version(
                new WorkspaceId(c.model().workspaceId().value()), c.model().name(), c.version().versionNo(),
                c.version().author(), Optional.ofNullable(c.version().comment()), c.version().createdAt(),
                c.version().contentHash(),
                c.xml()));
    }

    @Override
    public void recordGitSha(ModelId model, long versionNo, GitSha sha) {
        versions.recordGitSha(modeling(model), versionNo, sha.value());
    }

    @Override
    public Optional<Published> lastPublished(ModelId model) {
        return versions.lastPublished(modeling(model))
                .map(v -> new Published(v.versionNo(), GitSha.of(Optional.ofNullable(v.gitSha()).orElseThrow())));
    }

    @Override
    public WorkspaceId visibleWorkspace(EditorIdentity actor, ModelId model) {
        return new WorkspaceId(versions.visibleModel(actor, modeling(model)).workspaceId().value());
    }

    private static uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId modeling(ModelId model) {
        return uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId.of(model.value());
    }
}
