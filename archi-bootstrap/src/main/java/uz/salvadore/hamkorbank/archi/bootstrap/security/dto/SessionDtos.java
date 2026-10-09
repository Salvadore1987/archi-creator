package uz.salvadore.hamkorbank.archi.bootstrap.security.dto;

import java.util.List;

/** Ответы о сессии браузера: кто вошёл и куда идти за токеном. */
public final class SessionDtos {

    private SessionDtos() {
    }

    /** Текущий пользователь; роли — из полномочий {@code ROLE_*}, в порядке VIEWER, ARCHITECT, ADMIN. */
    public record Me(String subject, String displayName, List<String> roles) {
    }

    /** Без поля {@code oidc} — входа нет, запросы идут без токена. */
    public record UiConfig(OidcConfig oidc) {
    }

    public record OidcConfig(String authority, String clientId) {
    }
}
