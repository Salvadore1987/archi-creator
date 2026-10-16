package uz.salvadore.hamkorbank.archi.advisor.domain.common;

/**
 * Значение не проходит правило типа: пустое имя, отрицательный расход, неверный
 * формат идентификатора. Вместо текста несёт сообщение с ключом.
 */
public final class InvalidValueException extends AdvisorDomainException {

    /** Отказ с причиной-сообщением. */
    public InvalidValueException(Message reason) {
        super(AdvisorCodes.INVALID_INPUT, Failure.UNPROCESSABLE, reason);
    }

    /** Отказ с причиной по ключу и аргументам. */
    public InvalidValueException(String key, Object... args) {
        this(Message.of(key, args));
    }
}
