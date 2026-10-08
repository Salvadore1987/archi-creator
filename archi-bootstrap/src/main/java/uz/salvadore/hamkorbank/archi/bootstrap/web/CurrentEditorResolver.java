package uz.salvadore.hamkorbank.archi.bootstrap.web;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Автор команды — параметр {@code EditorIdentity} контроллера. Субъект — {@code sub}
 * из JWT, роли — {@code ROLE_*} из {@code realm_access} (KeycloakRealmRolesConverter),
 * группы — claim {@code groups} для списка доступа модели. Персональных
 * данных нет: имя и почта в домен не попадают.
 *
 * <p>Контроллеры не знают ни Spring Security, ни формата токена: заглушка профиля
 * {@code dev} и Keycloak в {@code prod} дают им одно и то же.
 */
public final class CurrentEditorResolver implements HandlerMethodArgumentResolver {

    private static final String GROUPS_CLAIM = "groups";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(EditorIdentity.class);
    }

    @Override
    public EditorIdentity resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                          NativeWebRequest request, WebDataBinderFactory binders) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("запрос без аутентификации");
        }
        return of(authentication);
    }

    static EditorIdentity of(Authentication authentication) {
        Set<String> groups = Set.of();
        String subject = authentication.getName();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            subject = jwt.getToken().getSubject() != null ? jwt.getToken().getSubject() : subject;
            List<String> claim = jwt.getToken().getClaimAsStringList(GROUPS_CLAIM);
            if (claim != null) {
                groups = claim.stream().map(g -> g.startsWith("/") ? g.substring(1) : g).collect(Collectors.toSet());
            }
        }
        return new EditorIdentity(subject, roles(authentication.getAuthorities()), groups);
    }

    private static Set<Role> roles(Collection<? extends GrantedAuthority> authorities) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority authority : authorities) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                for (Role role : Role.values()) {
                    if (role.name().equals(name.substring("ROLE_".length()))) {
                        roles.add(role);
                    }
                }
            }
        }
        return roles;
    }
}
