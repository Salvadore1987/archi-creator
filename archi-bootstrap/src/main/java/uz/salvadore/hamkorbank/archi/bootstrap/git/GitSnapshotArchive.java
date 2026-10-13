package uz.salvadore.hamkorbank.archi.bootstrap.git;

import java.util.Optional;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublishingService;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.port.GitBinding;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotArchive;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Порты modeling, которые закрывает Git: доступен ли репозиторий пространства
 * и откуда вернуть очищенный снимок. Сценарий публикации берётся лениво: он сам
 * зависит от версий modeling, и прямая ссылка замкнула бы граф бинов в кольцо.
 */
public final class GitSnapshotArchive implements SnapshotArchive, GitBinding {

    private final Supplier<GitPublishingService> publishing;

    public GitSnapshotArchive(Supplier<GitPublishingService> publishing) {
        this.publishing = publishing;
    }

    @Override
    public Optional<byte[]> restore(ModelHeader model, ModelVersion version) {
        return publishing.get().restore(new WorkspaceId(model.workspaceId().value()), new ModelId(model.id().value()),
                version.versionNo(), GitSha.of(version.gitSha().orElseThrow()), version.contentHash());
    }

    @Override
    public boolean boundAndReachable(uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId workspaceId) {
        return publishing.get().reachable(new WorkspaceId(workspaceId.value()));
    }
}
