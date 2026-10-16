package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import uz.salvadore.hamkorbank.archi.advisor.application.port.WorkspaceDirectory;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ResolveWorkspaceQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;

/** Пространство запроса по правилу modeling: заданное или первое по умолчанию. */
public final class ModelingWorkspaceDirectory implements WorkspaceDirectory {

    private final WorkspaceApplicationService workspaces;

    public ModelingWorkspaceDirectory(WorkspaceApplicationService workspaces) {
        this.workspaces = workspaces;
    }

    @Override
    public WorkspaceId resolve(WorkspaceId requested) {
        try {
            return WorkspaceId.of(workspaces.resolveWorkspace(
                    new ResolveWorkspaceQuery(requested == null ? null : requested.value())).workspaceId());
        } catch (ModelingDomainException missing) {
            throw AdvisorDomainException.notFound(Message.of(AdvisorMessages.WORKSPACE,
                    requested == null ? "" : requested.toString()));
        }
    }
}
