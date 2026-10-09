package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
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

    /**
     * Пространство команды: заданное клиентом — или первое, если клиент его не назвал.
     * Пространств нет вовсе — отказ «не найдено», а не сбой.
     */
    public WorkspaceId resolve(Optional<WorkspaceId> requested) {
        return requested.orElseGet(() -> list().stream().findFirst()
                .orElseThrow(() -> ModelingException.notFound(Message.of(ModelingMessages.ANY_WORKSPACE))).id());
    }

    public Workspace ensureDefault(String name) {
        return unitOfWork.write(() -> workspaces.findAll().stream().findFirst().orElseGet(() -> {
            Workspace workspace = new Workspace(WorkspaceId.next(new UuidV7(clock)), name, clock.instant());
            workspaces.insert(workspace);
            return workspace;
        }));
    }
}
