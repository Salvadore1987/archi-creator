package uz.salvadore.hamkorbank.archi.interchange.domain.common;

import java.util.Objects;

/**
 * Значение не проходит правило типа: пустое имя, отрицательный размер, неверный
 * формат идентификатора. Остаётся {@link IllegalArgumentException}, чтобы место
 * перехвата не менялось, но вместо текста несёт сообщение с ключом.
 */
public final class InvalidValueException extends IllegalArgumentException {

    private final Message reason;

    public InvalidValueException(Message reason) {
        super(Objects.requireNonNull(reason, "reason").toString());
        this.reason = reason;
    }

    public InvalidValueException(String key, Object... args) {
        this(Message.of(key, args));
    }

    public Message reason() {
        return reason;
    }
}
