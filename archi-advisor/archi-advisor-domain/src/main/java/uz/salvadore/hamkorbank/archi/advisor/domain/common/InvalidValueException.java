package uz.salvadore.hamkorbank.archi.advisor.domain.common;

import java.util.Objects;

/**
 * Значение не проходит правило типа: пустое имя, отрицательный расход, неверный
 * формат идентификатора. Вместо текста несёт сообщение с ключом.
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

    /** Причина отказа — ключ сообщения и аргументы. */
    public Message reason() {
        return reason;
    }
}
