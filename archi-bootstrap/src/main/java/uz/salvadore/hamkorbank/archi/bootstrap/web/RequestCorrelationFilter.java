package uz.salvadore.hamkorbank.archi.bootstrap.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Корреляция логов запроса (docs/backend.md §8.4, spec/nfr/*.yaml#observability.logging):
 * {@code traceId} — из заголовка W3C {@code traceparent} ({@code correlation_id:
 * from_traceparent}) или новый, {@code user} — subject токена, {@code modelId} — из пути.
 * В профиле {@code prod} логи — JSON (ECS), и поля MDC выходят отдельными полями.
 *
 * <p>Персональных данных в контексте нет: subject — не имя и не почта
 * ({@code pii_fields: []}). Маскирование сводится к тому, что маскировать нечего,
 * и это держит именно этот фильтр: в MDC не кладётся ничего, кроме трёх ключей.
 *
 * <p>Стоит после цепочки безопасности: subject известен только там.
 */
public final class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";

    private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
    private static final Pattern MODEL_PATH = Pattern.compile("^/api/v1/models/([0-9a-fA-F-]{36})(/.*)?$");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = traceId(request.getHeader("traceparent"));
        MDC.put("traceId", traceId);
        response.setHeader(TRACE_HEADER, traceId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            MDC.put("user", CurrentEditorResolver.of(authentication).subject());
        }
        Matcher model = MODEL_PATH.matcher(request.getRequestURI());
        if (model.matches()) {
            MDC.put("modelId", model.group(1));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
            MDC.remove("user");
            MDC.remove("modelId");
        }
    }

    static String traceId(String traceparent) {
        if (traceparent != null) {
            Matcher matcher = TRACEPARENT.matcher(traceparent.trim());
            if (matcher.matches() && !matcher.group(1).equals("0".repeat(32))) {
                return matcher.group(1);
            }
        }
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        return HexFormat.of().formatHex(random);
    }
}
