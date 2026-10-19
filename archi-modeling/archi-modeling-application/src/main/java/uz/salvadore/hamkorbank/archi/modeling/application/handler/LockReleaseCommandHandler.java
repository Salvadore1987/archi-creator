package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReleaseLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelLockReleasedEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Снятие блокировки: владелец — свою, {@code ADMIN} — любую. */
public final class LockReleaseCommandHandler {

    private final ModelingKernel kernel;

    public LockReleaseCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Снимает блокировку, если она есть; кто вправе — решает сама блокировка. */
    public void handle(EditorIdentity actor, ReleaseLockCommand command) {
        ModelId modelId = ModelId.of(command.modelId());
        kernel.run(Operation.RELEASE_LOCK, actor, () -> kernel.unitOfWork().write(() -> {
            kernel.visibleHeader(modelId, actor, AclAccess.READ);
            kernel.locks().find(modelId).ifPresent(lock -> {
                ModelLockReleasedEvent released = lock.release(actor, kernel.now());
                kernel.locks().delete(modelId);
                kernel.events().publish(List.of(released));
            });
            return null;
        }));
    }
}
