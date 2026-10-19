package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.WorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.WorkspaceDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;

/** Список рабочих пространств. */
public final class WorkspaceListQueryHandler {

    private final WorkspaceRepository workspaces;
    private final UnitOfWork unitOfWork;

    public WorkspaceListQueryHandler(WorkspaceRepository workspaces, UnitOfWork unitOfWork) {
        this.workspaces = workspaces;
        this.unitOfWork = unitOfWork;
    }

    /** Все пространства; их единицы — список без предела. */
    public List<WorkspaceResponse> handle() {
        return unitOfWork.read(() -> workspaces.findAll().stream().map(WorkspaceDataMapper::response).toList());
    }
}
