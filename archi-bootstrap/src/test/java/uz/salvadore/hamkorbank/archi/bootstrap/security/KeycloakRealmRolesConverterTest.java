package uz.salvadore.hamkorbank.archi.bootstrap.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Роли модели доступа в токене Keycloak лежат в {@code realm_access.roles}.
 * Тест держит два обещания разом: известные роли доходят до контекста
 * безопасности, неизвестные в него не попадают.
 */
class KeycloakRealmRolesConverterTest {

    private final KeycloakRealmRolesConverter converter = new KeycloakRealmRolesConverter();

    @Test
    @DisplayName("роли realm_access превращаются в ROLE_*")
    void realmRolesBecomeAuthorities() {
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithRealmRoles(List.of("ARCHITECT", "VIEWER")));

        assertEquals(Set.of("ROLE_ARCHITECT", "ROLE_VIEWER"), names(authorities));
    }

    @Test
    @DisplayName("роли вне модели доступа отбрасываются")
    void unknownRolesAreDropped() {
        Collection<GrantedAuthority> authorities =
                converter.convert(jwtWithRealmRoles(List.of("ADMIN", "offline_access", "default-roles-archi")));

        assertEquals(Set.of("ROLE_ADMIN"), names(authorities));
    }

    @Test
    @DisplayName("токен без realm_access даёт пустой набор, а не отказ")
    void tokenWithoutRealmAccessGivesNoAuthorities() {
        Jwt jwt = jwt(Map.of("sub", "user-1"));

        assertTrue(converter.convert(jwt).isEmpty());
    }

    @Test
    @DisplayName("realm_access без списка ролей даёт пустой набор")
    void realmAccessWithoutRolesGivesNoAuthorities() {
        Jwt jwt = jwt(Map.of("sub", "user-1", "realm_access", Map.of("something", "else")));

        assertTrue(converter.convert(jwt).isEmpty());
    }

    private static Set<String> names(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
    }

    private static Jwt jwtWithRealmRoles(List<String> roles) {
        return jwt(Map.of("sub", "user-1", "realm_access", Map.of("roles", roles)));
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                claims);
    }
}
