package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;

/** Отказы interchange — в {@code application/problem+json} (§8.1). */
@RestControllerAdvice
public class InterchangeProblemHandler {

    @ExceptionHandler(InterchangeException.class)
    ProblemDetail interchange(InterchangeException e, HttpServletRequest request) {
        HttpStatus status = switch (e.failure()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case GONE -> HttpStatus.GONE;
        };
        String message = e.getMessage().startsWith(e.code() + ": ")
                ? e.getMessage().substring(e.code().length() + 2) : e.getMessage();
        return problem(status, e.code(), message, e.details(), request);
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail, Map<String, Object> details,
                                 HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://archi-creator/errors/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(status == HttpStatus.BAD_REQUEST ? "Некорректный файл" : "Импорт и экспорт");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        details.forEach(problem::setProperty);
        return problem;
    }
}
