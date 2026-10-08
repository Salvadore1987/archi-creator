package uz.salvadore.hamkorbank.archi.modeling.domain.lock;

/** {@code HELD → RELEASED | EXPIRED}; EXPIRED наступает по времени, а не по команде. */
public enum LockStatus {
    HELD,
    RELEASED,
    EXPIRED
}
