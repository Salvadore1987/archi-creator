package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Problem Details RFC 7807 с полем {@code code}. Класс отказа домена определяет HTTP-код
 * и заголовок ответа на языке запроса.
 */
public final class ProblemFactory {

    private static final String TYPE_BASE = "https://archi-creator/errors/";

    private ProblemFactory() {
    }

    /** HTTP-статус для класса отказа. */
    static HttpStatus status(Failure failure) {
        return switch (failure) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case GONE -> HttpStatus.GONE;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DEFECT -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Ответ {@code problem+json}: тип по коду ошибки, заголовок на языке запроса, адрес запроса и дополнительные поля
     * отказа.
     */
    public static ProblemDetail problem(Failure failure, String code, String detail, Map<String, Object> details,
                                        HttpServletRequest request, TextCatalog text) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status(failure), detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(text.text(Message.of(title(failure))));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        details.forEach(problem::setProperty);
        return problem;
    }

    /** Ключ заголовка по классу отказа. */
    private static String title(Failure failure) {
        return switch (failure) {
            case NOT_FOUND -> ModelingMessages.TITLE_NOT_FOUND;
            case CONFLICT -> ModelingMessages.TITLE_CONFLICT;
            case UNPROCESSABLE -> ModelingMessages.TITLE_UNPROCESSABLE;
            case FORBIDDEN -> ModelingMessages.TITLE_FORBIDDEN;
            case GONE -> ModelingMessages.TITLE_GONE;
            case UNAVAILABLE -> ModelingMessages.TITLE_UNAVAILABLE;
            case DEFECT -> ModelingMessages.TITLE_INTERNAL;
        };
    }
}
