package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelStatus;

/** Захват, продление, освобождение и принудительное снятие блокировки. */
public final class LockService {

    private final ModelingKernel kernel;

    public LockService(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Захват или продление своей. Чужая действующая — {@code 409} с владельцем. */
    public ModelLock acquire(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.ACQUIRE_LOCK, actor, () -> kernel.unitOfWork.write(() -> {
            ModelHeader header = kernel.visibleHeader(modelId, actor, AclAccess.WRITE);
            if (header.status() != ModelStatus.ACTIVE) {
                throw new ModelingException(ModelingCodes.LIFECYCLE, Failure.CONFLICT,
                        Message.of(ModelingMessages.DELETED_NOT_EDITABLE));
            }
            var current = kernel.locks.find(modelId);
            current.filter(l -> !l.heldBy(actor.subject(), kernel.now()) && l.status(kernel.now())
                            == uz.salvadore.hamkorbank.archi.modeling.domain.lock.LockStatus.HELD)
                    .ifPresent(l -> kernel.metrics.lockWait(modelId,
                            java.time.Duration.between(kernel.now(), l.expiresAt())));
            ModelLock.Acquisition acquisition =
                    ModelLock.acquire(modelId, current, actor, kernel.now(), kernel.lockTtl);
            kernel.locks.save(acquisition.lock());
            acquisition.expiredPrevious().ifPresent(e -> kernel.events.publish(List.of(e)));
            return acquisition.lock();
        }));
    }

    /** Владелец снимает свою, {@code ADMIN} — любую; нет блокировки — делать нечего. */
    public void release(EditorIdentity actor, ModelId modelId) {
        kernel.run(Operation.RELEASE_LOCK, actor, () -> kernel.unitOfWork.write(() -> {
            kernel.visibleHeader(modelId, actor, AclAccess.READ);
            Optional<ModelLock> lock = kernel.locks.find(modelId);
            lock.ifPresent(l -> {
                var released = l.release(actor, kernel.now());
                kernel.locks.delete(modelId);
                kernel.events.publish(List.of(released));
            });
            return null;
        }));
    }
}
