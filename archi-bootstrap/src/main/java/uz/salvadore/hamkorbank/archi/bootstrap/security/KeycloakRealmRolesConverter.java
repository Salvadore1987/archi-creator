package uz.salvadore.hamkorbank.archi.bootstrap.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Достаёт роли из токена Keycloak и превращает их в полномочия Spring Security.
 *
 * <p>Keycloak кладёт роли realm'а в {@code realm_access.roles} — вложенным
 * объектом, а не плоским списком, поэтому стандартный
 * {@code JwtGrantedAuthoritiesConverter} их не видит и приложение получает
 * аутентифицированного пользователя без единой роли.
 *
 * <p>Из токена берутся только три роли модели доступа: {@code VIEWER}, {@code ARCHITECT},
 * {@code ADMIN}. Всё остальное, что realm мог выдать (например
 * {@code offline_access} или {@code default-roles-archi}), отбрасывается:
 * полномочие, которого нет в модели доступа, не должно попадать в контекст
 * безопасности и создавать видимость права.
 */
public final class KeycloakRealmRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /** Роли модели доступа. Порядок не важен, важна закрытость набора. */
    static final Set<String> KNOWN_ROLES = Set.of("VIEWER", "ARCHITECT", "ADMIN");

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String AUTHORITY_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return realmRoles(jwt).stream()
                .filter(KNOWN_ROLES::contains)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(AUTHORITY_PREFIX + role))
                .toList();
    }

    private static List<String> realmRoles(Jwt jwt) {
        if (!(jwt.getClaim(REALM_ACCESS) instanceof Map<?, ?> realmAccess)) {
            return List.of();
        }
        if (!(realmAccess.get(ROLES) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
