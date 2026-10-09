package uz.salvadore.hamkorbank.archi.bootstrap.security;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.bootstrap.security.dto.SessionDtos.Me;
import uz.salvadore.hamkorbank.archi.bootstrap.security.dto.SessionDtos.OidcConfig;
import uz.salvadore.hamkorbank.archi.bootstrap.security.dto.SessionDtos.UiConfig;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Кто вошёл и как входить. Инфраструктура, а не сценарий модели: интерфейс строит
 * себя по роли и узнаёт адрес входа раньше, чем у него появится токен.
 */
@RestController
@RequestMapping("/api/v1")
@EnableConfigurationProperties(UiSettings.class)
public class SessionController {

    private static final String AUTHORITY_PREFIX = "ROLE_";

    private final UiSettings settings;

    public SessionController(UiSettings settings) {
        this.settings = settings;
    }

    @GetMapping("/me")
    public Me me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("запрос без аутентификации");
        }
        String subject = authentication.getName();
        String displayName = subject;
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            subject = jwt.getSubject() != null ? jwt.getSubject() : subject;
            displayName = firstPresent(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"),
                    subject);
        }
        return new Me(subject, displayName, roles(authentication));
    }

    /** Открыт без токена: по нему фронтенд узнаёт, куда вести на вход. */
    @GetMapping("/ui-config")
    public UiConfig uiConfig() {
        UiSettings.Oidc oidc = settings.oidc();
        return new UiConfig(oidc.enabled() ? new OidcConfig(oidc.authority(), oidc.clientId()) : null);
    }

    private static List<String> roles(Authentication authentication) {
        Set<String> granted = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith(AUTHORITY_PREFIX))
                .map(a -> a.substring(AUTHORITY_PREFIX.length()))
                .collect(Collectors.toSet());
        return Arrays.stream(Role.values()).map(Enum::name).filter(granted::contains).toList();
    }

    private static String firstPresent(String... candidates) {
        return Arrays.stream(candidates).filter(c -> c != null && !c.isBlank()).findFirst().orElse("");
    }
}
