package uz.salvadore.hamkorbank.archi.modeling.domain.common;

/**
 * Значение не проходит правило типа: пустое имя, отрицательный размер, неверный
 * формат идентификатора. Отказ неверного ввода — тот же код, что у неразборчивого запроса.
 */
public final class InvalidValueException extends ModelingDomainException {

    /** Отказ с причиной-сообщением. */
    public InvalidValueException(Message reason) {
        super(ModelingCodes.INVALID_INPUT, Failure.UNPROCESSABLE, reason);
    }

    /** Отказ с причиной по ключу и аргументам. */
    public InvalidValueException(String key, Object... args) {
        this(Message.of(key, args));
    }
}
