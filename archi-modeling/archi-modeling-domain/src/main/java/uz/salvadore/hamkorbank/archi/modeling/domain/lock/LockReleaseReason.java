package uz.salvadore.hamkorbank.archi.modeling.domain.lock;

/** Причина снятия блокировки — попадает в событие {@code ModelLockReleased}. */
public enum LockReleaseReason {
    RELEASED_BY_OWNER,
    FORCED_BY_ADMIN,
    EXPIRED
}
