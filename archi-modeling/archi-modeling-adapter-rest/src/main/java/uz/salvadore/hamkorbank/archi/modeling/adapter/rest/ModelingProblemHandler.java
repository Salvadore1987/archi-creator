package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationNotPermittedException;

/** Отказы modeling — в {@code application/problem+json} (§8.1, §8.2). */
@RestControllerAdvice
public class ModelingProblemHandler {

    @ExceptionHandler(ModelingException.class)
    ProblemDetail modeling(ModelingException e, HttpServletRequest request) {
        return ProblemFactory.problem(ProblemFactory.status(e.failure()), e.code(), message(e), e.details(), request);
    }

    /** Недопустимая связь — {@code 422} с причиной и перечнем допустимых (UC-MDL-003, §8.2). */
    @ExceptionHandler(RelationNotPermittedException.class)
    ProblemDetail relation(RelationNotPermittedException e, HttpServletRequest request) {
        return ProblemFactory.problem(HttpStatus.UNPROCESSABLE_CONTENT, e.code(),
                "Связь " + e.relationship() + " от " + e.source().simpleName() + " к " + e.target().simpleName()
                        + " не разрешена в ArchiMate 3.2",
                Map.of("permitted", e.permitted().stream().map(t -> t.archiType().value()).sorted()
                        .collect(Collectors.toList())),
                request);
    }

    /** Сообщение без префикса кода: код уже стоит в своём поле. */
    private static String message(ModelingException e) {
        String message = e.getMessage();
        String prefix = e.code() + ": ";
        return message.startsWith(prefix) ? message.substring(prefix.length()) : message;
    }
}
