package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureImportPolicyCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportPolicyResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Смена строгости импорта пространства; итог перечитывается в той же транзакции. */
public final class ImportPolicyConfigureCommandHandler {

    private final InterchangeKernel kernel;
    private final ImportPolicy policy;

    public ImportPolicyConfigureCommandHandler(InterchangeKernel kernel, ImportPolicy policy) {
        this.kernel = kernel;
        this.policy = policy;
    }

    /** Новая настройка — как её записало хранилище. */
    public ImportPolicyResponse handle(EditorIdentity actor, ConfigureImportPolicyCommand command) {
        return kernel.run(InterchangeOperation.CONFIGURE_IMPORT_POLICY, actor, () -> kernel.unitOfWork().write(() -> {
            WorkspaceId workspace = new WorkspaceId(command.workspaceId());
            policy.setStrictImport(workspace, command.strictImport());
            return new ImportPolicyResponse(command.workspaceId(), policy.strictImport(workspace));
        }));
    }
}
