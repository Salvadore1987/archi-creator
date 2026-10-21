package uz.salvadore.hamkorbank.archi.interchange.application.dto.query;

import java.util.Objects;
import java.util.UUID;

/**
 * Очищенный снимок версии из её коммита.
 *
 * @param gitSha      коммит, в который версия опубликована
 * @param contentHash отпечаток содержимого версии — снимок из коммита обязан с ним совпасть
 */
public record RestoreGitSnapshotQuery(UUID workspaceId, UUID modelId, long versionNo, String gitSha,
                                      String contentHash) {

    public RestoreGitSnapshotQuery {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(gitSha, "gitSha");
        Objects.requireNonNull(contentHash, "contentHash");
    }
}
