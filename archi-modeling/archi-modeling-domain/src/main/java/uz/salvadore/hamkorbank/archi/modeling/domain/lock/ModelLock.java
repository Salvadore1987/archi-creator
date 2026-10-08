package uz.salvadore.hamkorbank.archi.modeling.domain.lock;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelLockReleased;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Право единолично редактировать модель — агрегат (INV-MDL-006, UC-MDL-005).
 *
 * <p>Хранится только действующая или просроченная запись: снятая блокировка строки
 * не оставляет. Просроченная прав не даёт, даже если запись ещё существует.
 */
public record ModelLock(ModelId modelId, String owner, Instant acquiredAt, Instant expiresAt) {

    public static final String INVARIANT = "INV-MDL-006";

    public ModelLock {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(acquiredAt)) {
            throw new IllegalArgumentException("срок блокировки истекает раньше захвата");
        }
    }

    public LockStatus status(Instant now) {
        return now.isBefore(expiresAt) ? LockStatus.HELD : LockStatus.EXPIRED;
    }

    public boolean heldBy(String subject, Instant now) {
        return status(now) == LockStatus.HELD && owner.equals(subject);
    }

    /**
     * Захват. Чужая действующая блокировка — {@code 409} с её владельцем; своя —
     * продление; просроченная — захват с событием о её истечении.
     */
    public static Acquisition acquire(ModelId modelId, Optional<ModelLock> current, EditorIdentity by, Instant now,
                                      Duration ttl) {
        Optional<ModelLockReleased> expired = Optional.empty();
        if (current.isPresent()) {
            ModelLock lock = current.get();
            if (lock.status(now) == LockStatus.HELD && !lock.owner.equals(by.subject())) {
                throw heldByOther(lock);
            }
            if (lock.status(now) == LockStatus.EXPIRED) {
                expired = Optional.of(new ModelLockReleased(modelId, lock.owner, lock.owner, LockReleaseReason.EXPIRED,
                        now));
            } else {
                return new Acquisition(new ModelLock(modelId, lock.owner, lock.acquiredAt, now.plus(ttl)), expired);
            }
        }
        return new Acquisition(new ModelLock(modelId, by.subject(), now, now.plus(ttl)), expired);
    }

    /**
     * Снятие. Владелец снимает свою, {@code ADMIN} — любую принудительно (FR-05);
     * архитектору чужую снимать нельзя.
     */
    public ModelLockReleased release(EditorIdentity by, Instant now) {
        if (status(now) == LockStatus.EXPIRED) {
            return new ModelLockReleased(modelId, owner, owner, LockReleaseReason.EXPIRED, now);
        }
        if (owner.equals(by.subject())) {
            return new ModelLockReleased(modelId, owner, by.subject(), LockReleaseReason.RELEASED_BY_OWNER, now);
        }
        if (by.isAdmin()) {
            return new ModelLockReleased(modelId, owner, by.subject(), LockReleaseReason.FORCED_BY_ADMIN, now);
        }
        throw new ModelingException(ModelingException.Codes.ACCESS_DENIED, Failure.FORBIDDEN,
                "чужую блокировку снимает только ADMIN (FR-05)", Map.of("lockOwner", owner));
    }

    /**
     * Запись требует действующей блокировки автора команды (INV-MDL-006). Иначе — {@code 409}:
     * клиент показывает владельца и предлагает read-only.
     */
    public static void requireWriteAccess(ModelId modelId, Optional<ModelLock> lock, String subject, Instant now) {
        if (lock.isEmpty()) {
            throw new ModelingException(INVARIANT, Failure.CONFLICT,
                    "модель " + modelId + " не заблокирована на редактирование: сначала захватите блокировку");
        }
        if (lock.get().status(now) == LockStatus.EXPIRED) {
            throw new ModelingException(INVARIANT, Failure.CONFLICT,
                    "блокировка модели " + modelId + " истекла: перезахватите её и повторите",
                    Map.of("lockOwner", lock.get().owner, "expiresAt", lock.get().expiresAt.toString()));
        }
        if (!lock.get().owner.equals(subject)) {
            throw heldByOther(lock.get());
        }
    }

    private static ModelingException heldByOther(ModelLock lock) {
        return new ModelingException(INVARIANT, Failure.CONFLICT,
                "модель редактирует " + lock.owner + " до " + lock.expiresAt,
                Map.of("lockOwner", lock.owner, "expiresAt", lock.expiresAt.toString()));
    }

    /** Итог захвата: новая блокировка и, если была просроченная, событие о её истечении. */
    public record Acquisition(ModelLock lock, Optional<ModelLockReleased> expiredPrevious) {
    }
}
