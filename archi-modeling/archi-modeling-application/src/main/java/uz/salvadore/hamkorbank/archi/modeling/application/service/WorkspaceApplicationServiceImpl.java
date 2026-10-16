package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EnsureDefaultWorkspaceCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ResolveWorkspaceQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ResolveWorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.WorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.WorkspaceEnsureDefaultCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.WorkspaceListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.WorkspaceResolveQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;

/** Рабочие пространства: делегирует в handler'ы сценариев. */
public final class WorkspaceApplicationServiceImpl implements WorkspaceApplicationService {

    private final WorkspaceListQueryHandler list;
    private final WorkspaceResolveQueryHandler resolve;
    private final WorkspaceEnsureDefaultCommandHandler ensureDefault;

    public WorkspaceApplicationServiceImpl(WorkspaceListQueryHandler list, WorkspaceResolveQueryHandler resolve,
                                           WorkspaceEnsureDefaultCommandHandler ensureDefault) {
        this.list = list;
        this.resolve = resolve;
        this.ensureDefault = ensureDefault;
    }

    @Override
    public List<WorkspaceResponse> listWorkspaces() {
        return list.handle();
    }

    @Override
    public ResolveWorkspaceResponse resolveWorkspace(ResolveWorkspaceQuery query) {
        return resolve.handle(query);
    }

    @Override
    public WorkspaceResponse ensureDefaultWorkspace(EnsureDefaultWorkspaceCommand command) {
        return ensureDefault.handle(command);
    }
}
