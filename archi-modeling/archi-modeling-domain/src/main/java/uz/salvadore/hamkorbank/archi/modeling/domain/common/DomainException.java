package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Map;
import java.util.Objects;

/**
 * Корень отказов домена: код для клиента API, вид отказа, по которому адаптер выбирает
 * HTTP-статус, сообщение с ключом и подробности. Текст для человека не хранится —
 * адаптер переводит сообщение на язык запроса.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final Failure failure;
    private final Message reason;
    private final Map<String, Object> details;

    /**
     * Отказ с кодом, видом, причиной и подробностями.
     *
     * @param details то, что нужно клиенту для решения: владелец блокировки, мешающие связи
     */
    protected DomainException(String code, Failure failure, Message reason, Map<String, Object> details) {
        super(Objects.requireNonNull(code, "code") + ": " + Objects.requireNonNull(reason, "reason"));
        this.code = code;
        this.failure = Objects.requireNonNull(failure, "failure");
        this.reason = reason;
        this.details = Map.copyOf(details);
    }

    /** Код отказа для клиента API — константа из {@link ModelingCodes}. */
    public String code() {
        return code;
    }

    /** Вид отказа, по которому адаптер выбирает HTTP-статус. */
    public Failure failure() {
        return failure;
    }

    /** Сообщение с ключом, которое адаптер переводит на язык запроса. */
    public Message reason() {
        return reason;
    }

    /** Подробности отказа для клиента: владелец блокировки, мешающие связи и подобное. */
    public Map<String, Object> details() {
        return details;
    }
}
