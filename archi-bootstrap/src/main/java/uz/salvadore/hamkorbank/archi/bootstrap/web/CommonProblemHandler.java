package uz.salvadore.hamkorbank.archi.bootstrap.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;

/**
 * Отказы, которые не принадлежат ни одному контексту, — в тот же {@code problem+json}.
 * Конфликт версии строки и нарушение ключа на коммите — {@code 409}: для
 * пользователя это «модель правит кто-то ещё», а не сбой сервера.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class CommonProblemHandler {

    /** Код отказа на неразборчивый запрос: тело, параметр или часть формы не читаются. */
    public static final String BAD_REQUEST = "BAD_REQUEST";
    private static final String TYPE_BASE = "https://archi-creator/errors/";

    private final MessageSource messages;

    public CommonProblemHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimistic(OptimisticLockingFailureException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ModelingCodes.LOCK_REQUIRED, text(BootstrapMessages.CONCURRENT_MODIFICATION),
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ModelingCodes.INTEGRITY_CONFLICT, text(BootstrapMessages.INTEGRITY_CONFLICT),
                request);
    }

    /** Файл больше предела — {@code 413} до чтения тела. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, InterchangeCodes.FILE_TOO_LARGE, text(BootstrapMessages.FILE_TOO_LARGE),
                request);
    }

    /** Причину разбора отдаёт Spring — технический текст, он идёт аргументом без перевода. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(Exception e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST, text(BootstrapMessages.BAD_REQUEST, e.getMessage()), request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(status == HttpStatus.CONFLICT ? text(BootstrapMessages.TITLE_CONFLICT) : status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
