package uz.salvadore.hamkorbank.archi.bootstrap.security;

import java.util.Arrays;
import java.util.Comparator;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uz.salvadore.hamkorbank.archi.bootstrap.security.dto.SessionDtos.Me;
import uz.salvadore.hamkorbank.archi.bootstrap.security.dto.SessionDtos.OidcConfig;
import uz.salvadore.hamkorbank.archi.bootstrap.security.dto.SessionDtos.UiConfig;
import uz.salvadore.hamkorbank.archi.bootstrap.web.CurrentEditorResolver;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Ответы о сессии браузера из аутентификации и настроек входа. */
final class SessionDtoMapper {

    private SessionDtoMapper() {
    }

    /**
     * Кто вошёл: субъект и роли — тем же разбором, что автор команды; имя для показа — {@code name},
     * затем {@code preferred_username} токена, иначе субъект.
     */
    static Me me(Authentication authentication) {
        EditorIdentity editor = CurrentEditorResolver.of(authentication);
        String displayName = editor.subject();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            displayName = firstPresent(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"),
                    editor.subject());
        }
        return new Me(editor.subject(), displayName, editor.roles().stream()
                .sorted(Comparator.naturalOrder()).map(Enum::name).toList());
    }

    /** Выключенный вход — ответ без {@code oidc}: фронтенд не уводит на вход. */
    static UiConfig uiConfig(UiSettings.Oidc oidc) {
        return new UiConfig(oidc.enabled() ? new OidcConfig(oidc.authority(), oidc.clientId()) : null);
    }

    private static String firstPresent(@Nullable String... candidates) {
        return Arrays.stream(candidates).filter(c -> c != null && !c.isBlank()).findFirst().orElse("");
    }
}
