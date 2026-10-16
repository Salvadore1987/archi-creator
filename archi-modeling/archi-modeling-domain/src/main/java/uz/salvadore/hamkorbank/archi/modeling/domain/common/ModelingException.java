package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Map;
import java.util.Objects;

/**
 * Отказ домена modeling. {@code code} — код из {@link ModelingCodes}: он уходит в поле
 * {@code code} ответа {@code problem+json} и в label {@code error_code} метрик.
 * Текст для человека не хранится — только сообщение с ключом, которое адаптер
 * переводит на язык запроса. К отказу прикладываются {@code details} — то, что нужно
 * клиенту для решения: владелец блокировки, список мешающих связей.
 */
public class ModelingException extends RuntimeException {

    private final String code;
    private final Failure failure;
    private final Message reason;
    private final Map<String, Object> details;

    public ModelingException(String code, Failure failure, Message reason) {
        this(code, failure, reason, Map.of());
    }

    public ModelingException(String code, Failure failure, Message reason, Map<String, Object> details) {
        super(code + ": " + reason);
        this.code = Objects.requireNonNull(code, "code");
        this.failure = Objects.requireNonNull(failure, "failure");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.details = Map.copyOf(details);
    }

    /** Объекта нет — или он скрыт: {@code what} называет его сообщением, а не строкой. */
    public static ModelingException notFound(Message what) {
        return new ModelingException(ModelingCodes.NOT_FOUND, Failure.NOT_FOUND,
                Message.of(ModelingMessages.NOT_FOUND, what));
    }

    /** Отказ в неразборчивом вводе: поле не задано или значение не того формата. */
    public static ModelingException invalid(Message reason) {
        return new ModelingException(ModelingCodes.INVALID_INPUT, Failure.UNPROCESSABLE, reason);
    }

    /** Значение не прошло правило типа — тот же отказ, что и неверный ввод. */
    public static ModelingException invalid(IllegalArgumentException invalid) {
        return invalid(invalid instanceof InvalidValueException value ? value.reason()
                : Message.of(ModelingMessages.INVALID_VALUE));
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
