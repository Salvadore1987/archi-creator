package uz.salvadore.hamkorbank.archi.modeling.application.mapper;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.WorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;

/** Рабочее пространство — в результат сценария. */
public final class WorkspaceDataMapper {

    private WorkspaceDataMapper() {
    }

    /** Пространство как результат: ключ и название. */
    public static WorkspaceResponse response(Workspace workspace) {
        return new WorkspaceResponse(workspace.id().value(), workspace.name());
    }
}
