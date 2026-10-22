package uz.salvadore.hamkorbank.archi.bootstrap.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tools.jackson.core.JacksonException;
import uz.salvadore.hamkorbank.archi.bootstrap.wiring.ErrorCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;

/**
 * Отказы, которые не принадлежат ни одному контексту, — в тот же {@code problem+json}.
 * Конфликт версии строки и нарушение ключа на коммите — {@code 409}: для
 * пользователя это «модель правит кто-то ещё», а не сбой сервера.
 *
 * <p>Обработчик — последняя линия: отказ, который не перевёл ни один контекст, тоже уходит
 * {@code problem+json} с кодом. Отказ самого Spring MVC сохраняет свой статус, неожиданный
 * сбой — {@code 500} со стеком в журнале и без подробностей в ответе. Отказы безопасности
 * идут дальше, к фильтру Spring Security: им отвечает {@code 401} и {@code 403} он.
 * Обработчики контекстов стоят раньше этого — у них явный порядок.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class CommonProblemHandler {

    private static final String TYPE_BASE = "https://archi-creator/errors/";
    private static final Logger LOG = LoggerFactory.getLogger(CommonProblemHandler.class);

    private final MessageSource messages;

    public CommonProblemHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimistic(OptimisticLockingFailureException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ModelingCodes.LOCK_REQUIRED,
                text(BootstrapMessages.CONCURRENT_MODIFICATION), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ModelingCodes.INTEGRITY_CONFLICT,
                text(BootstrapMessages.INTEGRITY_CONFLICT), request);
    }

    /** Файл больше предела — {@code 413} до чтения тела. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, InterchangeCodes.FILE_TOO_LARGE,
                text(BootstrapMessages.FILE_TOO_LARGE), request);
    }

    /**
     * Неразборчивый запрос: в ответе — что не разобрано, именем из запроса (параметр, часть формы,
     * путь поля в теле). Текст Spring и парсера JSON несёт типы Java — он уходит в журнал.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class,
            MultipartException.class})
    ProblemDetail badRequest(Exception e, HttpServletRequest request) {
        LOG.info("request not parsed: {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST,
                text(BootstrapMessages.BAD_REQUEST, unreadable(e)), request);
    }

    /**
     * Тело не прошло проверку формы — поле не задано, элемент списка {@code null}: перечень путей
     * полей, код и текст — контекста обработчика.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e, HandlerMethod handler, HttpServletRequest request) {
        return invalid(RequestViolations.fields(e), handler, request);
    }

    /** То же для тела-массива: ограничения на его элементах проверяет проверка метода. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail invalidArguments(HandlerMethodValidationException e, HandlerMethod handler,
                                   HttpServletRequest request) {
        return invalid(RequestViolations.fields(e), handler, request);
    }

    private ProblemDetail invalid(List<String> fields, HandlerMethod handler, HttpServletRequest request) {
        FormRefusal refusal = FormRefusal.of(handler.getBeanType());
        return problem(refusal.status(), refusal.code(), text(refusal.key(fields.size()), String.join(", ", fields)),
                request);
    }

    /** Отказ аутентификации и доступа — не ответ, а сигнал фильтру безопасности: он и отвечает. */
    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    void security(RuntimeException e) {
        throw e;
    }

    /**
     * Отказ, не переведённый никем: у Spring MVC — его статус с заголовками (у {@code 405} —
     * {@code Allow}), у прочего — {@code 500}. Начатый ответ — поток, который клиент бросил, или
     * истёкший срок потока — не переписывается: тело уже уходит.
     */
    @ExceptionHandler(Exception.class)
    @Nullable ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request,
                                                      HttpServletResponse response) {
        if (response.isCommitted()) {
            LOG.info("failure after response started: {} {}", request.getMethod(), request.getRequestURI(), e);
            return null;
        }
        if (e instanceof ErrorResponse refused) {
            HttpStatusCode status = refused.getStatusCode();
            return ResponseEntity.status(status).headers(refused.getHeaders())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem(status, status.isSameCodeAs(HttpStatus.BAD_REQUEST) ? ErrorCodes.BAD_REQUEST
                                    : ErrorCodes.REQUEST_NOT_SUPPORTED,
                            text(BootstrapMessages.REQUEST_NOT_SUPPORTED, request.getMethod(),
                                    request.getRequestURI()), request));
        }
        LOG.error("unexpected failure: {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR,
                        text(BootstrapMessages.INTERNAL_ERROR), request));
    }

    private ProblemDetail problem(HttpStatusCode status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(text(title(status)));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }

    private String unreadable(Exception e) {
        return switch (e) {
            case MethodArgumentTypeMismatchException mismatch ->
                    text(BootstrapMessages.UNREADABLE_PARAMETER, mismatch.getName());
            case MissingServletRequestParameterException missing ->
                    text(BootstrapMessages.MISSING_PARAMETER, missing.getParameterName());
            case MissingServletRequestPartException missing ->
                    text(BootstrapMessages.MISSING_PART, missing.getRequestPartName());
            case MultipartException form -> text(BootstrapMessages.UNREADABLE_FORM);
            default -> field(e).map(path -> text(BootstrapMessages.UNREADABLE_FIELD, path))
                    .orElseGet(() -> text(BootstrapMessages.UNREADABLE_BODY));
        };
    }

    /** Путь поля тела, на котором споткнулся разбор JSON: {@code nodes[2].id}. */
    private static Optional<String> field(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException json) {
                StringBuilder path = new StringBuilder();
                for (JacksonException.Reference step : json.getPath()) {
                    if (step.getPropertyName() != null) {
                        path.append(path.isEmpty() ? "" : ".").append(step.getPropertyName());
                    } else if (step.getIndex() >= 0) {
                        path.append('[').append(step.getIndex()).append(']');
                    }
                }
                return path.isEmpty() ? Optional.empty() : Optional.of(path.toString());
            }
        }
        return Optional.empty();
    }

    /** Ключ заголовка по статусу; статус Spring MVC вне списка — общий «запрос не поддерживается». */
    private static String title(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> BootstrapMessages.TITLE_BAD_REQUEST;
            case 404 -> BootstrapMessages.TITLE_NOT_FOUND;
            case 405 -> BootstrapMessages.TITLE_METHOD_NOT_ALLOWED;
            case 406 -> BootstrapMessages.TITLE_NOT_ACCEPTABLE;
            case 409 -> BootstrapMessages.TITLE_CONFLICT;
            case 413 -> BootstrapMessages.TITLE_TOO_LARGE;
            case 415 -> BootstrapMessages.TITLE_UNSUPPORTED_MEDIA_TYPE;
            case 422 -> BootstrapMessages.TITLE_UNPROCESSABLE;
            case 500 -> BootstrapMessages.TITLE_INTERNAL;
            default -> BootstrapMessages.TITLE_NOT_SUPPORTED;
        };
    }

    private String text(String key, Object... args) {
        return Objects.requireNonNullElse(messages.getMessage(key, args, key, LocaleContextHolder.getLocale()), key);
    }
}
