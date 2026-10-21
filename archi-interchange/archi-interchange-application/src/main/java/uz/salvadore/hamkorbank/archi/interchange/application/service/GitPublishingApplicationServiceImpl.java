package uz.salvadore.hamkorbank.archi.interchange.application.service;

import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.PushModelToGitCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.RestoreGitSnapshotQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ModelGitStatusResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitOutboxPublishCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitReachabilityQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitSnapshotRestoreQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelGitPushCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitPublishingApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Публикация в Git: делегирует в handler'ы сценариев. */
public final class GitPublishingApplicationServiceImpl implements GitPublishingApplicationService {

    private final ModelGitPushCommandHandler push;
    private final GitOutboxPublishCommandHandler outbox;
    private final GitReachabilityQueryHandler reachability;
    private final GitSnapshotRestoreQueryHandler restore;

    public GitPublishingApplicationServiceImpl(ModelGitPushCommandHandler push, GitOutboxPublishCommandHandler outbox,
                                               GitReachabilityQueryHandler reachability,
                                               GitSnapshotRestoreQueryHandler restore) {
        this.push = push;
        this.outbox = outbox;
        this.reachability = reachability;
        this.restore = restore;
    }

    @Override
    public ModelGitStatusResponse pushModelToGit(EditorIdentity actor, PushModelToGitCommand command) {
        return push.handle(actor, command);
    }

    @Override
    public int publishPendingVersions() {
        return outbox.handle();
    }

    @Override
    public boolean repositoryReachable(UUID workspaceId) {
        return reachability.handle(workspaceId);
    }

    @Override
    public Optional<byte[]> restoreSnapshot(RestoreGitSnapshotQuery query) {
        return restore.handle(query);
    }
}
