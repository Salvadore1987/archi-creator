package uz.salvadore.hamkorbank.archi.advisor.domain.common;

import java.util.Map;

/**
 * Отказ помощника. {@code code} — код из {@link AdvisorCodes}: он уходит в поле
 * {@code code} ответа {@code problem+json} и в label {@code error_code} метрик.
 *
 * @param details то, что нужно клиенту для решения: дата сброса бюджета,
 *                секунды до следующей попытки
 */
public class AdvisorDomainException extends DomainException {

    /** Отказ без подробностей. */
    public AdvisorDomainException(String code, Failure failure, Message reason) {
        this(code, failure, reason, Map.of());
    }

    /** Отказ с подробностями для клиента: дата сброса бюджета, секунды до повтора. */
    public AdvisorDomainException(String code, Failure failure, Message reason, Map<String, Object> details) {
        super(code, failure, reason, details);
    }

    /** Объект не найден; {@code what} — подпись объекта, переводится вместе с фразой. */
    public static AdvisorDomainException notFound(Message what) {
        return new AdvisorDomainException(AdvisorCodes.NOT_FOUND, Failure.NOT_FOUND,
                Message.of(AdvisorMessages.NOT_FOUND, what));
    }

    /** Неверный ввод с причиной. */
    public static AdvisorDomainException invalid(Message reason) {
        return new AdvisorDomainException(AdvisorCodes.INVALID_INPUT, Failure.UNPROCESSABLE, reason);
    }

    /** Значение не разобрано средствами JDK (разбор даты, {@code valueOf}) — тот же отказ, что и неверный ввод. */
    public static AdvisorDomainException invalid(IllegalArgumentException invalid) {
        return invalid(Message.of(AdvisorMessages.INVALID_VALUE));
    }
}
