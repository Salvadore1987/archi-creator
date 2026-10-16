package uz.salvadore.hamkorbank.archi.modeling.application.mapper;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LockResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;

/** Блокировка модели — в результат сценария. */
public final class LockDataMapper {

    private LockDataMapper() {
    }

    /** Блокировка как результат: модель, владелец и срок. */
    public static LockResponse response(ModelLock lock) {
        return new LockResponse(lock.modelId().value(), lock.owner(), lock.acquiredAt(), lock.expiresAt());
    }
}
