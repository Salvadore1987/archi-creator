package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.RestoreGitSnapshotQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublisher;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Очищенный снимок версии из её коммита. Вход modeling для выгрузки старой версии: роль у этой
 * операции проверяет вызывающий.
 */
public final class GitSnapshotRestoreQueryHandler {

    private final GitPublisher publisher;

    public GitSnapshotRestoreQueryHandler(GitPublisher publisher) {
        this.publisher = publisher;
    }

    /** Пусто — привязки нет или каталога модели в коммите нет. */
    public Optional<byte[]> handle(RestoreGitSnapshotQuery query) {
        return publisher.restore(new WorkspaceId(query.workspaceId()), new ModelId(query.modelId()),
                query.versionNo(), GitSha.of(query.gitSha()), query.contentHash());
    }
}
