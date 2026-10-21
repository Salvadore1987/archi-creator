package uz.salvadore.hamkorbank.archi.bootstrap.git;

import java.util.Optional;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.RestoreGitSnapshotQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitPublishingApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.GitBinding;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotArchive;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Порты modeling, которые закрывает Git: доступен ли репозиторий пространства
 * и откуда вернуть очищенный снимок. Сценарий публикации берётся лениво: он сам
 * зависит от версий modeling, и прямая ссылка замкнула бы граф бинов в кольцо.
 */
public final class GitSnapshotArchive implements SnapshotArchive, GitBinding {

    private final Supplier<GitPublishingApplicationService> publishing;

    public GitSnapshotArchive(Supplier<GitPublishingApplicationService> publishing) {
        this.publishing = publishing;
    }

    @Override
    public Optional<byte[]> restore(ModelHeader model, ModelVersion version) {
        return publishing.get().restoreSnapshot(new RestoreGitSnapshotQuery(model.workspaceId().value(),
                model.id().value(), version.versionNo(), Optional.ofNullable(version.gitSha()).orElseThrow(),
                version.contentHash()));
    }

    @Override
    public boolean boundAndReachable(WorkspaceId workspaceId) {
        return publishing.get().repositoryReachable(workspaceId.value());
    }
}
