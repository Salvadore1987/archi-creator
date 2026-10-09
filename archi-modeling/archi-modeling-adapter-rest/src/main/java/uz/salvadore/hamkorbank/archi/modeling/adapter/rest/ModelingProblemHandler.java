package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationNotPermittedException;

/** Отказы modeling — в {@code application/problem+json}, текст — на языке запроса. */
@RestControllerAdvice
public class ModelingProblemHandler {

    private final TextCatalog text;

    public ModelingProblemHandler(TextCatalog text) {
        this.text = text;
    }

    @ExceptionHandler(ModelingException.class)
    ProblemDetail modeling(ModelingException e, HttpServletRequest request) {
        return ProblemFactory.problem(ProblemFactory.status(e.failure()), e.code(), text.text(e.reason()), e.details(),
                request, text);
    }

    /** Недопустимая связь — {@code 422} с причиной и перечнем допустимых. */
    @ExceptionHandler(RelationNotPermittedException.class)
    ProblemDetail relation(RelationNotPermittedException e, HttpServletRequest request) {
        return ProblemFactory.problem(HttpStatus.UNPROCESSABLE_CONTENT, e.code(), text.text(e.reason()),
                Map.of("permitted", e.permitted().stream().map(t -> t.archiType().value()).sorted()
                        .collect(Collectors.toList())),
                request, text);
    }
}
