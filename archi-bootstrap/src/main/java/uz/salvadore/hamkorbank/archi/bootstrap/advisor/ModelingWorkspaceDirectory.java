package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.application.port.WorkspaceDirectory;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.service.WorkspaceService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;

/** Пространство запроса по правилу modeling: заданное или первое по умолчанию. */
public final class ModelingWorkspaceDirectory implements WorkspaceDirectory {

    private final WorkspaceService workspaces;

    public ModelingWorkspaceDirectory(WorkspaceService workspaces) {
        this.workspaces = workspaces;
    }

    @Override
    public WorkspaceId resolve(Optional<WorkspaceId> requested) {
        try {
            return WorkspaceId.of(workspaces.resolve(requested.map(w ->
                    uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId.of(w.value()))).value());
        } catch (ModelingDomainException missing) {
            throw AdvisorException.notFound(Message.of(AdvisorMessages.WORKSPACE, requested.map(Object::toString)
                    .orElse("")));
        }
    }
}
