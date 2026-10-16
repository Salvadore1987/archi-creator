package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.AcquireLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LockResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.LockDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.LockStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Захват или продление блокировки; удалённая модель не блокируется. */
public final class LockAcquireCommandHandler {

    private final ModelingKernel kernel;

    public LockAcquireCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Блокировка автора команды; чужая действующая — отказ, а время ожидания — в метрику. */
    public LockResponse handle(EditorIdentity actor, AcquireLockCommand command) {
        ModelId modelId = ModelId.of(command.modelId());
        return kernel.run(Operation.ACQUIRE_LOCK, actor, () -> kernel.unitOfWork().write(() -> {
            kernel.visibleHeader(modelId, actor, AclAccess.WRITE).requireActive();
            Optional<ModelLock> current = kernel.locks().find(modelId);
            current.filter(l -> !l.heldBy(actor.subject(), kernel.now())
                            && l.status(kernel.now()) == LockStatus.HELD)
                    .ifPresent(l -> kernel.metrics().lockWait(modelId,
                            Duration.between(kernel.now(), l.expiresAt())));
            ModelLock.Acquisition acquisition =
                    ModelLock.acquire(modelId, current.orElse(null), actor, kernel.now(), kernel.lockTtl());
            kernel.locks().save(acquisition.lock());
            if (acquisition.expiredPrevious() != null) {
                kernel.events().publish(List.of(acquisition.expiredPrevious()));
            }
            return LockDataMapper.response(acquisition.lock());
        }));
    }
}
