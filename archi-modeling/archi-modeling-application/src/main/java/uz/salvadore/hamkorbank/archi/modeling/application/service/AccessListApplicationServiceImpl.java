package uz.salvadore.hamkorbank.archi.modeling.application.service;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceAccessListCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetAccessListQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.AccessListResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.AccessListGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.AccessListReplaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.AccessListApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Списки доступа: делегирует в handler'ы сценариев. */
public final class AccessListApplicationServiceImpl implements AccessListApplicationService {

    private final AccessListGetQueryHandler get;
    private final AccessListReplaceCommandHandler replace;

    public AccessListApplicationServiceImpl(AccessListGetQueryHandler get, AccessListReplaceCommandHandler replace) {
        this.get = get;
        this.replace = replace;
    }

    @Override
    public AccessListResponse getAccessList(EditorIdentity actor, GetAccessListQuery query) {
        return get.handle(actor, query);
    }

    @Override
    public AccessListResponse replaceAccessList(EditorIdentity actor, ReplaceAccessListCommand command) {
        return replace.handle(actor, command);
    }
}
