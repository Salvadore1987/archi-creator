package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Problem Details RFC 7807 с полем {@code code}. Класс отказа
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
                                        HttpServletRequest request, TextCatalog text) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(title(status).map(key -> text.text(Message.of(key))).orElse(status.getReasonPhrase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        details.forEach(problem::setProperty);
        return problem;
    }

    /** Ключ заголовка по статусу; для прочих статусов — стандартная фраза HTTP. */
    private static Optional<String> title(HttpStatus status) {
        return Optional.ofNullable(switch (status) {
            case NOT_FOUND -> ModelingMessages.TITLE_NOT_FOUND;
            case CONFLICT -> ModelingMessages.TITLE_CONFLICT;
            case UNPROCESSABLE_CONTENT -> ModelingMessages.TITLE_UNPROCESSABLE;
            case FORBIDDEN -> ModelingMessages.TITLE_FORBIDDEN;
            case GONE -> ModelingMessages.TITLE_GONE;
            case BAD_REQUEST -> ModelingMessages.TITLE_BAD_REQUEST;
            default -> null;
        });
    }
}
