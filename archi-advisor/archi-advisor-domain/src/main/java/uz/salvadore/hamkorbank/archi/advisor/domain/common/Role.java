package uz.salvadore.hamkorbank.archi.advisor.domain.common;

/** Роль автора обращения — из JWT, теми же именами, что в realm Keycloak. */
public enum Role {
    VIEWER,
    ARCHITECT,
    ADMIN
}
