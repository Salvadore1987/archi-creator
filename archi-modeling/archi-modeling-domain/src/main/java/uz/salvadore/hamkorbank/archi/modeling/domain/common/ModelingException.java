package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Map;
import java.util.Objects;

/**
 * Отказ домена modeling. {@code code} — код инварианта ({@code INV-MDL-004}) или
 * код ошибки ({@code RELATION_NOT_PERMITTED}): он уходит в поле {@code code}
 * ответа {@code problem+json} и в label {@code error_code} метрик.
 *
 * @param details то, что нужно клиенту для решения: владелец блокировки,
 *                список мешающих связей
 */
public class ModelingException extends RuntimeException {

    private final String code;
    private final Failure failure;
    private final Map<String, Object> details;

    public ModelingException(String code, Failure failure, String message) {
        this(code, failure, message, Map.of());
    }

    public ModelingException(String code, Failure failure, String message, Map<String, Object> details) {
        super(code + ": " + message);
        this.code = Objects.requireNonNull(code, "code");
        this.failure = Objects.requireNonNull(failure, "failure");
        this.details = Map.copyOf(details);
    }

    public static ModelingException notFound(String what) {
        return new ModelingException(Codes.NOT_FOUND, Failure.NOT_FOUND, what + " не найден(а)");
    }

    public static ModelingException invalid(String message) {
        return new ModelingException(Codes.INVALID_INPUT, Failure.UNPROCESSABLE, message);
    }

    public String code() {
        return code;
    }

    public Failure failure() {
        return failure;
    }

    public Map<String, Object> details() {
        return details;
    }

    /** Коды отказов, не являющиеся кодами инвариантов. */
    public static final class Codes {
        public static final String NOT_FOUND = "MDL_NOT_FOUND";
        public static final String INVALID_INPUT = "MDL_INVALID_INPUT";
        public static final String ACCESS_DENIED = "MDL_ACCESS_DENIED";
        public static final String TYPE_NOT_EDITABLE = "MDL_TYPE_NOT_EDITABLE";
        public static final String FOLDER_NOT_EMPTY = "MDL_FOLDER_NOT_EMPTY";
        public static final String SNAPSHOT_PURGED = "MDL_SNAPSHOT_PURGED";
        public static final String IDEMPOTENCY_CONFLICT = "MDL_IDEMPOTENCY_KEY_REUSED";

        private Codes() {
        }
    }
}
