package uz.salvadore.hamkorbank.archi.bootstrap.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Заглушка авторизации профиля {@code dev} (§10.3): каждый запрос приходит
 * от пользователя {@value #PRINCIPAL} с ролью {@code ARCHITECT}.
 *
 * <p>Нужна не ради удобства, а ради того, чтобы код за фильтром был одинаков
 * в dev и prod: use case читает субъект и роль из контекста безопасности
 * и не знает, что Keycloak не поднят. Без заглушки пришлось бы разводить
 * ветки «есть аутентификация / нет», и в prod оставалась бы непройденная.
 *
 * <p>Роль ровно одна и ровно {@code ARCHITECT}: операции {@code ADMIN}
 * (принудительное снятие блокировки, физическое удаление модели) в dev
 * должны быть недоступны так же, как архитектору в бою, — иначе dev
 * перестаёт ловить неверную роль на границе use case'а.
 */
public final class DevArchitectAuthenticationFilter extends OncePerRequestFilter {

    /** Субъект, который видит приложение вместо {@code sub} из токена. */
    public static final String PRINCIPAL = "dev-architect";

    private static final UsernamePasswordAuthenticationToken STUB =
            UsernamePasswordAuthenticationToken.authenticated(
                    PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_ARCHITECT")));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(STUB);
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
