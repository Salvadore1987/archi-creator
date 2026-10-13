package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.time.Instant;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Версия модели зафиксирована коммитом. Публикуется после того, как {@code gitSha}
 * уже записан в версию: событие сообщает о свершившемся.
 *
 * @param changedFileCount сколько файлов каталога модели изменил коммит; ноль —
 *                         содержимое уже было в ветке, коммит не понадобился
 */
public record GitSnapshotCommitted(WorkspaceId workspaceId, ModelId modelId, long versionNo, GitSha gitSha,
                                   String branch, int changedFileCount, Instant occurredAt) {

    public GitSnapshotCommitted {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(gitSha, "gitSha");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
