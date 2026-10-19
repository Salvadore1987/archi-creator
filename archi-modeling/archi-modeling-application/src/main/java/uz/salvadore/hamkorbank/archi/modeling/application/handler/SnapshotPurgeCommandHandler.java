package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.modeling.application.port.GitBinding;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.SnapshotRetention;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Очистка снимков по правилу ретеншена — системный проход по расписанию. Не трогает
 * пространство без настроенного и доступного репозитория: там снимок — единственная копия.
 * Доступность Git спрашивается один раз на пространство за проход.
 */
public final class SnapshotPurgeCommandHandler {

    private final ModelingKernel kernel;
    private final GitBinding gitBinding;
    private final SnapshotRetention retention;
    private final boolean enabled;

    public SnapshotPurgeCommandHandler(ModelingKernel kernel, GitBinding gitBinding, ZoneId zone, boolean enabled) {
        this.kernel = kernel;
        this.gitBinding = gitBinding;
        this.retention = new SnapshotRetention(zone);
        this.enabled = enabled;
    }

    /** Сколько снимков очищено; ретеншен выключен — ноль. */
    public int handle() {
        if (!enabled) {
            return 0;
        }
        int purged = 0;
        Map<WorkspaceId, Boolean> reachable = new HashMap<>();
        for (ModelId modelId : kernel.unitOfWork().read(kernel.versions()::modelsWithSnapshots)) {
            WorkspaceId workspace = kernel.unitOfWork().read(() -> kernel.models().findHeader(modelId).orElseThrow())
                    .workspaceId();
            boolean gitBound = reachable.computeIfAbsent(workspace, gitBinding::boundAndReachable);
            purged += kernel.unitOfWork().write(() -> {
                List<ModelVersion> purgeable = retention.purgeable(kernel.versions().list(modelId), kernel.now(),
                        gitBound);
                purgeable.forEach(v -> kernel.versions().save(v.snapshotPurged(gitBound)));
                return purgeable.size();
            });
        }
        return purged;
    }
}
