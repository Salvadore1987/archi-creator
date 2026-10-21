package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;

/**
 * Отказы modeling — в {@code application/problem+json}, текст — на языке запроса.
 * Стоит раньше общего обработчика: у того последняя линия на любое исключение.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ModelingProblemHandler {

    private final TextCatalog text;

    public ModelingProblemHandler(TextCatalog text) {
        this.text = text;
    }

    @ExceptionHandler(ModelingDomainException.class)
    ProblemDetail modeling(ModelingDomainException e, HttpServletRequest request) {
        return ProblemFactory.problem(e.failure(), e.code(), text.text(e.reason()), e.details(),
                request, text);
    }
}
