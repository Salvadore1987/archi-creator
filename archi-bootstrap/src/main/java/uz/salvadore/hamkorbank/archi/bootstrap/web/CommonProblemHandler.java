package uz.salvadore.hamkorbank.archi.bootstrap.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Отказы, которые не принадлежат ни одному контексту, — в тот же {@code problem+json}
 * (§8.1). Конфликт версии строки и нарушение ключа на коммите — {@code 409}: для
 * пользователя это «модель правит кто-то ещё» (NFR-07), а не сбой сервера.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class CommonProblemHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimistic(OptimisticLockingFailureException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "INV-MDL-006",
                "Модель изменена параллельно: перечитайте состояние и повторите", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "MDL_INTEGRITY_CONFLICT",
                "Изменение противоречит данным, записанным параллельно: перечитайте и повторите", request);
    }

    /** Файл больше предела — {@code 413} до чтения тела (UC-IXC-001). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "IXC_FILE_TOO_LARGE", "Файл больше допустимого размера", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(Exception e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Некорректный запрос: " + e.getMessage(), request);
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://archi-creator/errors/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(status == HttpStatus.CONFLICT ? "Конфликт" : status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
