package uz.salvadore.hamkorbank.archi.bootstrap.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Субъект токена в журнале запроса — ключ {@code user} MDC. Стоит после цепочки безопасности:
 * субъект известен только там; {@code traceId} к этому времени уже положил {@link RequestCorrelationFilter}.
 */
public final class RequestUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            MDC.put("user", CurrentEditorResolver.of(authentication).subject());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("user");
        }
    }
}
