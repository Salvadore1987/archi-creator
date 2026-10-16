package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Map;

/**
 * Отказ домена modeling. {@code code} — код из {@link ModelingCodes}: он уходит в поле
 * {@code code} ответа {@code problem+json} и в label {@code error_code} метрик.
 */
public class ModelingDomainException extends DomainException {

    /** Отказ без подробностей. */
    public ModelingDomainException(String code, Failure failure, Message reason) {
        this(code, failure, reason, Map.of());
    }

    /** Отказ с подробностями для клиента. */
    public ModelingDomainException(String code, Failure failure, Message reason, Map<String, Object> details) {
        super(code, failure, reason, details);
    }
    /** Объекта нет — или он скрыт: {@code what} называет его сообщением, а не строкой. */
    public static ModelingDomainException notFound(Message what) {
        return new ModelingDomainException(ModelingCodes.NOT_FOUND, Failure.NOT_FOUND,
                Message.of(ModelingMessages.NOT_FOUND, what));
    }

    /** Отказ в неразборчивом вводе: поле не задано или значение не того формата. */
    public static ModelingDomainException invalid(Message reason) {
        return new ModelingDomainException(ModelingCodes.INVALID_INPUT, Failure.UNPROCESSABLE, reason);
    }

    /** Значение не разобрано средствами JDK ({@code valueOf}, разбор числа) — тот же отказ, что и неверный ввод. */
    public static ModelingDomainException invalid(IllegalArgumentException invalid) {
        return invalid(Message.of(ModelingMessages.INVALID_VALUE));
    }
}
