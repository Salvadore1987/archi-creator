package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Problem Details RFC 7807 с полем {@code code} (docs/backend.md §8.1). Класс отказа
 * домена определяет HTTP-код одинаково для modeling и interchange.
 */
public final class ProblemFactory {

    private static final String TYPE_BASE = "https://archi-creator/errors/";

    private ProblemFactory() {
    }

    public static HttpStatus status(Failure failure) {
        return switch (failure) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case GONE -> HttpStatus.GONE;
        };
    }

    public static ProblemDetail problem(HttpStatus status, String code, String detail, Map<String, Object> details,
                                        HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(title(status));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        details.forEach(problem::setProperty);
        return problem;
    }

    private static String title(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Не найдено";
            case CONFLICT -> "Конфликт";
            case UNPROCESSABLE_CONTENT -> "Нарушено правило";
            case FORBIDDEN -> "Нет доступа";
            case GONE -> "Больше недоступно";
            case BAD_REQUEST -> "Некорректный запрос";
            default -> status.getReasonPhrase();
        };
    }
}
