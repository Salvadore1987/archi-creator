package uz.salvadore.hamkorbank.archi.bootstrap.git;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RecordGitShaCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetCommittedVersionQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetLastPublishedVersionQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelSummaryQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Версии modeling для публикации: перевод идентификаторов двух контекстов и ничего больше. */
public final class ModelingPublishedVersions implements PublishedVersions {

    private final ModelExchangeApplicationService models;

    public ModelingPublishedVersions(ModelExchangeApplicationService models) {
        this.models = models;
    }

    @Override
    public Optional<Version> version(ModelId model, long versionNo) {
        return models.getCommittedVersion(new GetCommittedVersionQuery(model.value(), versionNo)).map(c -> new Version(
                new WorkspaceId(c.model().workspaceId()), c.model().name(), c.version().versionNo(),
                c.version().author(), c.version().comment(), c.version().createdAt(), c.version().contentHash(),
                c.xml()));
    }

    @Override
    public void recordGitSha(ModelId model, long versionNo, GitSha sha) {
        models.recordGitSha(new RecordGitShaCommand(model.value(), versionNo, sha.value()));
    }

    @Override
    public Optional<Published> lastPublished(ModelId model) {
        return models.getLastPublishedVersion(new GetLastPublishedVersionQuery(model.value()))
                .map(v -> new Published(v.versionNo(), GitSha.of(Optional.ofNullable(v.gitSha()).orElseThrow())));
    }

    @Override
    public WorkspaceId visibleWorkspace(EditorIdentity actor, ModelId model) {
        return new WorkspaceId(models.getModelSummary(actor, new GetModelSummaryQuery(model.value())).workspaceId());
    }
}
