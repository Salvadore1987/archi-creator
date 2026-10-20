package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Строгость импорта рабочего пространства: чтение и смена. */
public final class ImportService {

    private final ImportPolicy policy;
    private final UnitOfWork unitOfWork;
    private final InterchangeMetrics metrics;

    public ImportService(ImportPolicy policy, UnitOfWork unitOfWork, InterchangeMetrics metrics) {
        this.policy = policy;
        this.unitOfWork = unitOfWork;
        this.metrics = metrics;
    }

    /** Строгость импорта пространства меняет только {@code ADMIN}. */
    public void configureStrictImport(EditorIdentity actor, UUID workspaceId, boolean strict) {
        InterchangeOperation operation = InterchangeOperation.CONFIGURE_IMPORT_POLICY;
        metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            unitOfWork.write(() -> policy.setStrictImport(new WorkspaceId(workspaceId), strict));
            return null;
        });
    }

    /** Включён ли строгий режим импорта пространства. */
    public boolean strictImport(UUID workspaceId) {
        return unitOfWork.read(() -> policy.strictImport(new WorkspaceId(workspaceId)));
    }
}
