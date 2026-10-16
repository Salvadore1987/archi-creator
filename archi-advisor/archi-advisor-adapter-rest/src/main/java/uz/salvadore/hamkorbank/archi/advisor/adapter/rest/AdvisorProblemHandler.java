package uz.salvadore.hamkorbank.archi.advisor.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;

/**
 * Отказы помощника — в {@code application/problem+json}, текст — на языке запроса.
 * Превышение частоты несёт {@code Retry-After}, исчерпанный бюджет — дату сброса.
 */
@RestControllerAdvice
public class AdvisorProblemHandler {

    private static final String TYPE_BASE = "https://archi-creator/errors/";

    private final TextCatalog text;

    public AdvisorProblemHandler(TextCatalog text) {
        this.text = text;
    }

    @ExceptionHandler(AdvisorDomainException.class)
    ResponseEntity<ProblemDetail> advisor(AdvisorDomainException e, HttpServletRequest request) {
        HttpStatus status = status(e.failure());
        ProblemDetail problem = problem(status, e.code(), text.text(e.reason()), e.details(), request);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (e.details().get("retryAfterSeconds") instanceof Number seconds) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds.longValue()));
        }
        return response.body(problem);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, Map<String, Object> details,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(text.text(Message.of(title(status))));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        details.forEach(problem::setProperty);
        return problem;
    }

    private static HttpStatus status(Failure failure) {
        return switch (failure) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static String title(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> AdvisorMessages.TITLE_NOT_FOUND;
            case CONFLICT -> AdvisorMessages.TITLE_CONFLICT;
            case FORBIDDEN -> AdvisorMessages.TITLE_FORBIDDEN;
            case TOO_MANY_REQUESTS -> AdvisorMessages.TITLE_TOO_MANY_REQUESTS;
            case CONTENT_TOO_LARGE -> AdvisorMessages.TITLE_TOO_LARGE;
            case SERVICE_UNAVAILABLE -> AdvisorMessages.TITLE_UNAVAILABLE;
            default -> AdvisorMessages.TITLE_UNPROCESSABLE;
        };
    }
}
