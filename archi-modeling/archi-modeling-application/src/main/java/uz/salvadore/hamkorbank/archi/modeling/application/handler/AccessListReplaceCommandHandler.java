package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceAccessListCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.AccessListResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.AccessListDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.ModelAccessList;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.PrincipalType;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Замена списка доступа целиком. */
public final class AccessListReplaceCommandHandler {

    private final ModelingKernel kernel;

    public AccessListReplaceCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Новый список доступа; скрытая модель отвечает «не найдено» раньше, чем выдала бы себя отказом. */
    public AccessListResponse handle(EditorIdentity actor, ReplaceAccessListCommand command) {
        ModelId modelId = ModelId.of(command.modelId());
        return kernel.run(Operation.MANAGE_ACCESS, actor, () -> {
            List<AclEntry> entries = command.entries().stream().map(AccessListReplaceCommandHandler::entry).toList();
            return kernel.unitOfWork().write(() -> {
                ModelHeader header = kernel.visibleHeader(modelId, actor, AclAccess.READ);
                ModelAccessList list = ModelAccessList.replace(actor, header.createdBy(), modelId, entries);
                kernel.accessLists().save(list);
                return AccessListDataMapper.response(list);
            });
        });
    }

    private static AclEntry entry(ReplaceAccessListCommand.Entry entry) {
        if (entry == null || entry.principalType() == null || entry.access() == null) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.ACL_ENTRY_FORMAT));
        }
        try {
            return new AclEntry(PrincipalType.valueOf(entry.principalType()), entry.principal(),
                    AclAccess.valueOf(entry.access()));
        } catch (IllegalArgumentException | InvalidValueException invalid) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.ACL_ENTRY_FORMAT));
        }
    }
}
