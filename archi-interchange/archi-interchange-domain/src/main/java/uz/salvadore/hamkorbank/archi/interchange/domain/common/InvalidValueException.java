package uz.salvadore.hamkorbank.archi.interchange.domain.common;


/**
 * Значение не проходит правило типа: пустое имя, отрицательный размер, неверный
 * формат идентификатора. Остаётся {@link IllegalArgumentException}, чтобы место
 * перехвата не менялось, но вместо текста несёт сообщение с ключом.
 */
public final class InvalidValueException extends InterchangeDomainException {

    /** Отказ с причиной-сообщением. */
    public InvalidValueException(Message reason) {
        super(InterchangeCodes.INVALID_REQUEST, reason);
    }

    /** Отказ с причиной по ключу и аргументам. */
    public InvalidValueException(String key, Object... args) {
        this(Message.of(key, args));
    }
}
