package uz.salvadore.hamkorbank.archi.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ExpressionJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Роли модели доступа в токене Keycloak лежат в {@code realm_access.roles}. Тест держит два
 * обещания: выражение из {@code application.yaml} доводит роли до контекста безопасности,
 * а в сценарий из них попадают только роли модели доступа.
 */
class RealmRolesTest {

    private static final String PREFIX = "spring.security.oauth2.resourceserver.jwt.";
    private static final Instant ISSUED = Instant.parse("2026-10-01T09:00:00Z");

    private final ExpressionJwtGrantedAuthoritiesConverter converter = converterFromConfiguration();

    @Test
    @DisplayName("роли realm_access превращаются в ROLE_*")
    void realmRolesBecomeAuthorities() {
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithRealmRoles(List.of("ARCHITECT", "VIEWER")));

        assertEquals(Set.of("ROLE_ARCHITECT", "ROLE_VIEWER"), names(authorities));
    }

    @Test
    @DisplayName("роли вне модели доступа до сценария не доходят")
    void unknownRolesAreDropped() {
        Jwt jwt = jwtWithRealmRoles(List.of("ADMIN", "offline_access", "default-roles-archi"));

        Set<Role> roles = CurrentEditorResolver.of(new JwtAuthenticationToken(jwt, converter.convert(jwt))).roles();

        assertEquals(Set.of(Role.ADMIN), roles);
    }

    @Test
    @DisplayName("токен без realm_access даёт пустой набор, а не отказ")
    void tokenWithoutRealmAccessGivesNoAuthorities() {
        assertTrue(converter.convert(jwt(Map.of("sub", "user-1"))).isEmpty());
    }

    @Test
    @DisplayName("realm_access без списка ролей даёт пустой набор")
    void realmAccessWithoutRolesGivesNoAuthorities() {
        Jwt jwt = jwt(Map.of("sub", "user-1", "realm_access", Map.of("something", "else")));

        assertTrue(converter.convert(jwt).isEmpty());
    }

    /** Тот же разбор, что собирает Spring Boot, — по выражению и префиксу из настроек приложения. */
    private static ExpressionJwtGrantedAuthoritiesConverter converterFromConfiguration() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();
        ExpressionJwtGrantedAuthoritiesConverter converter = new ExpressionJwtGrantedAuthoritiesConverter(
                new SpelExpressionParser().parseExpression(
                        properties.getProperty(PREFIX + "authorities-claim-expressions")));
        converter.setAuthorityPrefix(properties.getProperty(PREFIX + "authority-prefix"));
        return converter;
    }

    private static Set<String> names(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static Jwt jwtWithRealmRoles(List<String> roles) {
        return jwt(Map.of("sub", "user-1", "realm_access", Map.of("roles", roles)));
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token-value", ISSUED, ISSUED.plusSeconds(300), Map.of("alg", "RS256"), claims);
    }
}
