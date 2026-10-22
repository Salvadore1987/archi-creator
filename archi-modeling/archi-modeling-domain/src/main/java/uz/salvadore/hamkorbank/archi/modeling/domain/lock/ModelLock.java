package uz.salvadore.hamkorbank.archi.modeling.domain.lock;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelLockReleasedEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Право единолично редактировать модель — агрегат.
 *
 * <p>Хранится только действующая или просроченная запись: снятая блокировка строки
 * не оставляет. Просроченная прав не даёт, даже если запись ещё существует.
 */
public final class ModelLock extends AggregateRoot<ModelId> {

    /** Код отказа при записи без действующей собственной блокировки модели. */
    public static final String INVARIANT = ModelingCodes.LOCK_REQUIRED;

    private final String owner;
    private final Instant acquiredAt;
    private final Instant expiresAt;

    private ModelLock(ModelId modelId, String owner, Instant acquiredAt, Instant expiresAt) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(acquiredAt)) {
            throw new InvalidValueException(ModelingMessages.LOCK_EXPIRES_BEFORE_ACQUIRED);
        }
        super(modelId);
        this.owner = owner;
        this.acquiredAt = acquiredAt;
        this.expiresAt = expiresAt;
    }

    /** Сборка из сохранённых значений — для хранилища; значения проверяются, как при создании. */
    public static ModelLock restore(ModelId modelId, String owner, Instant acquiredAt, Instant expiresAt) {
        return new ModelLock(modelId, owner, acquiredAt, expiresAt);
    }

    /** Действует ли блокировка в момент {@code now} или уже просрочена. */
    public LockStatus status(Instant now) {
        return now.isBefore(expiresAt) ? LockStatus.HELD : LockStatus.EXPIRED;
    }

    /** Даёт ли блокировка право на запись пользователю {@code subject} в момент {@code now}. */
    public boolean heldBy(String subject, Instant now) {
        return status(now) == LockStatus.HELD && owner.equals(subject);
    }

    /**
     * Захват. Чужая действующая блокировка — {@code 409} с её владельцем; своя —
     * продление; просроченная — захват с событием о её истечении. {@code null} — блокировки нет.
     */
    public static Acquisition acquire(ModelId modelId, @Nullable ModelLock current, EditorIdentity by, Instant now,
                                      Duration ttl) {
        ModelLockReleasedEvent expired = null;
        if (current != null) {
            ModelLock lock = current;
            if (lock.status(now) == LockStatus.HELD && !lock.owner.equals(by.subject())) {
                throw heldByOther(lock);
            }
            if (lock.status(now) == LockStatus.EXPIRED) {
                expired = new ModelLockReleasedEvent(modelId, lock.owner, lock.owner,
                        LockReleaseReason.EXPIRED, now);
            } else {
                return new Acquisition(new ModelLock(modelId, lock.owner, lock.acquiredAt, now.plus(ttl)), expired);
            }
        }
        return new Acquisition(new ModelLock(modelId, by.subject(), now, now.plus(ttl)), expired);
    }

    /**
     * Снятие. Владелец снимает свою, {@code ADMIN} — любую принудительно;
     * архитектору чужую снимать нельзя.
     */
    public ModelLockReleasedEvent release(EditorIdentity by, Instant now) {
        if (status(now) == LockStatus.EXPIRED) {
            return new ModelLockReleasedEvent(modelId(), owner, owner, LockReleaseReason.EXPIRED, now);
        }
        if (owner.equals(by.subject())) {
            return new ModelLockReleasedEvent(modelId(), owner, by.subject(), LockReleaseReason.RELEASED_BY_OWNER, now);
        }
        if (by.isAdmin()) {
            return new ModelLockReleasedEvent(modelId(), owner, by.subject(), LockReleaseReason.FORCED_BY_ADMIN, now);
        }
        throw new ModelingDomainException(ModelingCodes.ACCESS_DENIED, Failure.FORBIDDEN,
                Message.of(ModelingMessages.LOCK_FOREIGN_RELEASE), Map.of("lockOwner", owner));
    }

    /**
     * Запись требует действующей блокировки автора команды. Иначе — {@code 409}:
     * клиент показывает владельца и предлагает read-only. {@code null} — блокировки нет.
     */
    public static void requireWriteAccess(ModelId modelId, @Nullable ModelLock lock, String subject, Instant now) {
        if (lock == null) {
            throw new ModelingDomainException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.LOCK_NOT_HELD, modelId));
        }
        if (lock.status(now) == LockStatus.EXPIRED) {
            throw new ModelingDomainException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.LOCK_EXPIRED, modelId),
                    Map.of("lockOwner", lock.owner, "expiresAt", lock.expiresAt.toString()));
        }
        if (!lock.owner.equals(subject)) {
            throw heldByOther(lock);
        }
    }

    private static ModelingDomainException heldByOther(ModelLock lock) {
        return new ModelingDomainException(INVARIANT, Failure.CONFLICT,
                Message.of(ModelingMessages.LOCK_HELD_BY_OTHER, lock.owner, lock.expiresAt),
                Map.of("lockOwner", lock.owner, "expiresAt", lock.expiresAt.toString()));
    }

    /**
     * Итог захвата: новая блокировка и, если была просроченная, событие о её истечении.
     *
     * @param expiredPrevious событие об истечении прежней; {@code null} — просроченной не было
     */
    public record Acquisition(ModelLock lock, @Nullable ModelLockReleasedEvent expiredPrevious) {
    }

    /** Заблокированная модель. */
    public ModelId modelId() {
        return id();
    }

    /** Субъект, который держит блокировку. */
    public String owner() {
        return owner;
    }

    /** Момент захвата или последнего продления. */
    public Instant acquiredAt() {
        return acquiredAt;
    }

    /** Момент, после которого блокировка истекает сама. */
    public Instant expiresAt() {
        return expiresAt;
    }
}
