package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.time.Clock;
import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Рабочие пространства. Первое заводится при старте приложения, если их нет вовсе:
 * миграция строки не сажает — её {@code id} пришлось бы выдумать (журнал этапа 0).
 */
public final class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public WorkspaceService(WorkspaceRepository workspaces, UnitOfWork unitOfWork, Clock clock) {
        this.workspaces = workspaces;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public List<Workspace> list() {
        return unitOfWork.read(workspaces::findAll);
    }

    public Workspace ensureDefault(String name) {
        return unitOfWork.write(() -> workspaces.findAll().stream().findFirst().orElseGet(() -> {
            Workspace workspace = new Workspace(WorkspaceId.next(new UuidV7(clock)), name, clock.instant());
            workspaces.insert(workspace);
            return workspace;
        }));
    }
}
