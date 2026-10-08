package uz.salvadore.hamkorbank.archi.modeling.domain.access;

/** Кого называет запись списка доступа: пользователя по subject или группу Keycloak. */
public enum PrincipalType {
    USER,
    GROUP
}
