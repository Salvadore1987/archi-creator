package uz.salvadore.hamkorbank.archi.bootstrap.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Журнал входящих запросов: одна запись {@code INFO} на запрос — метод, путь, статус и длительность.
 * Строки запроса, заголовков и тел в журнале нет: в теле — модель пользователя, в заголовке — токен,
 * в параметрах — что угодно из адресной строки.
 *
 * <p>Стоит сразу после фильтра корреляции, до цепочки безопасности: её отказы {@code 401} и {@code 403}
 * тоже попадают в журнал и несут {@code traceId}. Поток помощника отвечает асинхронно — его запись
 * пишется, когда поток запроса отпущен, со статусом начала ответа.
 */
public final class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            LOG.info("http {} {} {} in {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }
}
