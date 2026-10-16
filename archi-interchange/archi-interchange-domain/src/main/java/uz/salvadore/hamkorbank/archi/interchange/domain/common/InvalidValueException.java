package uz.salvadore.hamkorbank.archi.interchange.domain.common;


/**
 * Значение не проходит правило типа: пустое имя, отрицательный размер, неверный
 * формат идентификатора. Отказ домена с кодом неразборчивого запроса и сообщением
 * с ключом, а не текстом.
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
