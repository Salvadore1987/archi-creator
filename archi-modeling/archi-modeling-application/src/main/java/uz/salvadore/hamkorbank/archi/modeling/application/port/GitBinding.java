package uz.salvadore.hamkorbank.archi.modeling.application.port;

import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Настроен ли и доступен ли Git у рабочего пространства. От ответа зависит очистка
 * снимков (FR-47). До этапа 7a реализация отвечает «нет» для всех.
 */
public interface GitBinding {

    boolean boundAndReachable(WorkspaceId workspaceId);
}
