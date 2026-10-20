package uz.salvadore.hamkorbank.archi.interchange.application.service;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureImportPolicyCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetImportPolicyQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportPolicyResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ImportPolicyConfigureCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ImportPolicyGetQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ImportPolicyApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Настройка импорта: делегирует в handler'ы сценариев. */
public final class ImportPolicyApplicationServiceImpl implements ImportPolicyApplicationService {

    private final ImportPolicyGetQueryHandler get;
    private final ImportPolicyConfigureCommandHandler configure;

    public ImportPolicyApplicationServiceImpl(ImportPolicyGetQueryHandler get,
                                              ImportPolicyConfigureCommandHandler configure) {
        this.get = get;
        this.configure = configure;
    }

    @Override
    public ImportPolicyResponse getImportPolicy(EditorIdentity actor, GetImportPolicyQuery query) {
        return get.handle(actor, query);
    }

    @Override
    public ImportPolicyResponse configureImportPolicy(EditorIdentity actor, ConfigureImportPolicyCommand command) {
        return configure.handle(actor, command);
    }
}
