package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ResolveWorkspaceQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ResolveWorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Пространство команды: заданное клиентом — или первое, если клиент его не назвал. Пространств
 * нет вовсе — отказ «не найдено», а не сбой.
 */
public final class WorkspaceResolveQueryHandler {

    private final WorkspaceRepository workspaces;
    private final UnitOfWork unitOfWork;

    public WorkspaceResolveQueryHandler(WorkspaceRepository workspaces, UnitOfWork unitOfWork) {
        this.workspaces = workspaces;
        this.unitOfWork = unitOfWork;
    }

    /** Пространство по запросу. */
    public ResolveWorkspaceResponse handle(ResolveWorkspaceQuery query) {
        if (query.workspaceId() != null) {
            return new ResolveWorkspaceResponse(query.workspaceId());
        }
        return new ResolveWorkspaceResponse(unitOfWork.read(workspaces::first)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.ANY_WORKSPACE)))
                .id().value());
    }
}
