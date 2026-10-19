package uz.salvadore.hamkorbank.archi.interchange.domain.common;

import java.util.Objects;

/**
 * Корень отказов домена interchange: код нарушенного правила и сообщение с ключом.
 * Вид отказа и HTTP-статус выбирает application-слой — домен о них не знает.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final Message reason;

    /** Отказ с кодом из {@link InterchangeCodes} и причиной-сообщением. */
    protected DomainException(String code, Message reason) {
        super(Objects.requireNonNull(code, "code") + ": " + Objects.requireNonNull(reason, "reason"));
        this.code = code;
        this.reason = reason;
    }

    /** Код нарушенного правила — константа из {@link InterchangeCodes}. */
    public String code() {
        return code;
    }

    /** Сообщение с ключом, которое переводится на язык запроса. */
    public Message reason() {
        return reason;
    }
}
