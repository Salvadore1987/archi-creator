package uz.salvadore.hamkorbank.archi.advisor.domain.common;

import java.util.Map;
import java.util.Objects;

/**
 * Отказ помощника. {@code code} — код из {@link AdvisorCodes}: он уходит в поле
 * {@code code} ответа {@code problem+json} и в label {@code error_code} метрик.
 *
 * @param details то, что нужно клиенту для решения: дата сброса бюджета,
 *                секунды до следующей попытки
 */
public class AdvisorException extends RuntimeException {

    private final String code;
    private final Failure failure;
    private final Message reason;
    private final Map<String, Object> details;

    public AdvisorException(String code, Failure failure, Message reason) {
        this(code, failure, reason, Map.of());
    }

    public AdvisorException(String code, Failure failure, Message reason, Map<String, Object> details) {
        super(code + ": " + reason);
        this.code = Objects.requireNonNull(code, "code");
        this.failure = Objects.requireNonNull(failure, "failure");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.details = Map.copyOf(details);
    }

    public static AdvisorException notFound(Message what) {
        return new AdvisorException(AdvisorCodes.NOT_FOUND, Failure.NOT_FOUND,
                Message.of(AdvisorMessages.NOT_FOUND, what));
    }

    public static AdvisorException invalid(Message reason) {
        return new AdvisorException(AdvisorCodes.INVALID_INPUT, Failure.UNPROCESSABLE, reason);
    }

    /** Значение не прошло правило типа — тот же отказ, что и неверный ввод. */
    public static AdvisorException invalid(IllegalArgumentException invalid) {
        return invalid(invalid instanceof InvalidValueException value ? value.reason()
                : Message.of(AdvisorMessages.INVALID_VALUE));
    }

    public String code() {
        return code;
    }

    public Failure failure() {
        return failure;
    }

    public Message reason() {
        return reason;
    }

    public Map<String, Object> details() {
        return details;
    }
}
