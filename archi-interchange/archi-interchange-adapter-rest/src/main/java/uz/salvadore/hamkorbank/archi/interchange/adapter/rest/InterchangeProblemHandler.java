package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.salvadore.hamkorbank.archi.interchange.application.ExportDefectException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** Отказы interchange — в {@code application/problem+json}, текст — на языке запроса. */
@RestControllerAdvice
public class InterchangeProblemHandler {

    private static final String TYPE_BASE = "https://archi-creator/errors/";
    private static final Logger LOG = LoggerFactory.getLogger(InterchangeProblemHandler.class);

    private final TextCatalog text;

    public InterchangeProblemHandler(TextCatalog text) {
        this.text = text;
    }

    @ExceptionHandler(InterchangeException.class)
    ProblemDetail interchange(InterchangeException e, HttpServletRequest request) {
        HttpStatus status = switch (e.failure()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case GONE -> HttpStatus.GONE;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return problem(status, e.code(), text.text(e.reason()), e.details(), request, text);
    }

    /** Дефект выгрузки — {@code 500}: подробность нарушения уходит в журнал, клиенту — код и текст. */
    @ExceptionHandler(ExportDefectException.class)
    ProblemDetail defect(ExportDefectException e, HttpServletRequest request) {
        LOG.error("{} {}: {}", e.code(), request.getRequestURI(), e.detail());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, e.code(), text.text(e.reason()), Map.of(), request, text);
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail, Map<String, Object> details,
                                 HttpServletRequest request, TextCatalog text) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(text.text(Message.of(status == HttpStatus.BAD_REQUEST
                ? InterchangeMessages.TITLE_BAD_FILE : InterchangeMessages.TITLE_INTERCHANGE)));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        details.forEach((name, value) -> problem.setProperty(name, FindingMapper.detail(value, text)));
        return problem;
    }
}
