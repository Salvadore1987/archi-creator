package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RecordGitShaCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceModelContentCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetCommittedVersionQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetLastPublishedVersionQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelContentQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelSummaryQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetVersionSnapshotQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CommittedVersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ReplaceModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionSnapshotResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.CommittedVersionGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelContentGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelContentReplaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelSummaryGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.PublishedVersionGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionGitShaRecordCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionSnapshotGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Вход для других контекстов: делегирует в handler'ы сценариев. */
public final class ModelExchangeApplicationServiceImpl implements ModelExchangeApplicationService {

    private final VersionSnapshotGetQueryHandler snapshot;
    private final ModelSummaryGetQueryHandler summary;
    private final ModelContentGetQueryHandler content;
    private final ModelContentReplaceCommandHandler replace;
    private final CommittedVersionGetQueryHandler committed;
    private final PublishedVersionGetQueryHandler published;
    private final VersionGitShaRecordCommandHandler gitSha;

    public ModelExchangeApplicationServiceImpl(VersionSnapshotGetQueryHandler snapshot,
                                               ModelSummaryGetQueryHandler summary,
                                               ModelContentGetQueryHandler content,
                                               ModelContentReplaceCommandHandler replace,
                                               CommittedVersionGetQueryHandler committed,
                                               PublishedVersionGetQueryHandler published,
                                               VersionGitShaRecordCommandHandler gitSha) {
        this.snapshot = snapshot;
        this.summary = summary;
        this.content = content;
        this.replace = replace;
        this.committed = committed;
        this.published = published;
        this.gitSha = gitSha;
    }

    @Override
    public VersionSnapshotResponse getVersionSnapshot(EditorIdentity actor, GetVersionSnapshotQuery query) {
        return snapshot.handle(actor, query);
    }

    @Override
    public ModelSummaryResponse getModelSummary(EditorIdentity actor, GetModelSummaryQuery query) {
        return summary.handle(actor, query);
    }

    @Override
    public ModelContentResponse getModelContent(EditorIdentity actor, GetModelContentQuery query) {
        return content.handle(actor, query);
    }

    @Override
    public ReplaceModelContentResponse replaceModelContent(EditorIdentity actor, ReplaceModelContentCommand command) {
        return replace.handle(actor, command);
    }

    @Override
    public Optional<CommittedVersionResponse> getCommittedVersion(GetCommittedVersionQuery query) {
        return committed.handle(query);
    }

    @Override
    public Optional<VersionResponse> getLastPublishedVersion(GetLastPublishedVersionQuery query) {
        return published.handle(query);
    }

    @Override
    public void recordGitSha(RecordGitShaCommand command) {
        gitSha.handle(command);
    }
}
