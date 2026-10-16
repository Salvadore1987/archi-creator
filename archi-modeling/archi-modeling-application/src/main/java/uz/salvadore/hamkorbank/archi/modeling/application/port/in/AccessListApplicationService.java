package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceAccessListCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetAccessListQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.AccessListResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Список доступа к модели: кто из авторов её видит и правит. */
public interface AccessListApplicationService {

    /** Список доступа; читать его может любой, кому модель видна. */
    AccessListResponse getAccessList(EditorIdentity actor, GetAccessListQuery query);

    /** Список заменяется целиком; задаёт его автор модели или {@code ADMIN}. */
    AccessListResponse replaceAccessList(EditorIdentity actor, ReplaceAccessListCommand command);
}
