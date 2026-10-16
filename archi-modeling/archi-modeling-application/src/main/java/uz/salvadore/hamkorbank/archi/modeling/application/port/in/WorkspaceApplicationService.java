package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EnsureDefaultWorkspaceCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ResolveWorkspaceQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ResolveWorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.WorkspaceResponse;

/** Рабочие пространства. */
public interface WorkspaceApplicationService {

    /** Все рабочие пространства. */
    List<WorkspaceResponse> listWorkspaces();

    /** Пространство команды: названное клиентом или первое; пространств нет вовсе — «не найдено». */
    ResolveWorkspaceResponse resolveWorkspace(ResolveWorkspaceQuery query);

    /** Первое пространство на пустой базе; есть хоть одно — возвращается первое как есть. */
    WorkspaceResponse ensureDefaultWorkspace(EnsureDefaultWorkspaceCommand command);
}
