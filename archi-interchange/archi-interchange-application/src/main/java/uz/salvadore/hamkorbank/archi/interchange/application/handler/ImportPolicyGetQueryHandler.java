package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetImportPolicyQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportPolicyResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Настройка импорта пространства — тем, у кого есть роль её читать. */
public final class ImportPolicyGetQueryHandler {

    private final InterchangeKernel kernel;
    private final ImportPolicy policy;

    public ImportPolicyGetQueryHandler(InterchangeKernel kernel, ImportPolicy policy) {
        this.kernel = kernel;
        this.policy = policy;
    }

    /** Текущая настройка. */
    public ImportPolicyResponse handle(EditorIdentity actor, GetImportPolicyQuery query) {
        return kernel.run(InterchangeOperation.GET_IMPORT_POLICY, actor, () -> new ImportPolicyResponse(
                query.workspaceId(),
                kernel.unitOfWork().read(() -> policy.strictImport(new WorkspaceId(query.workspaceId())))));
    }
}
