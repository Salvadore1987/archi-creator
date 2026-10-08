package uz.salvadore.hamkorbank.archi.modeling.domain.access;

/** Уровень доступа к модели. {@link #WRITE} включает {@link #READ}; роль он не расширяет. */
public enum AclAccess {
    READ,
    WRITE;

    public boolean covers(AclAccess needed) {
        return this == WRITE || needed == READ;
    }
}
