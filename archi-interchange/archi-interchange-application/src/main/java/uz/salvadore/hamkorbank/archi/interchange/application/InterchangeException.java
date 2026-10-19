package uz.salvadore.hamkorbank.archi.interchange.application;

import java.util.Map;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Отказ сценария interchange. Классы отказа — те же, что у modeling: адаптер REST
 * выводит из них коды ответа одинаково для обоих контекстов. Текста нет — только
 * сообщение с ключом, которое адаптер переводит на язык запроса.
 */
public class InterchangeException extends RuntimeException {

    private final String code;
    private final Failure failure;
    private final Message reason;
    private final Map<String, Object> details;

    public InterchangeException(String code, Failure failure, Message reason, Map<String, Object> details) {
        super(code + ": " + reason);
        this.code = Objects.requireNonNull(code, "code");
        this.failure = Objects.requireNonNull(failure, "failure");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.details = Map.copyOf(details);
    }

    /** Код ошибки для клиента API. */
    public String code() {
        return code;
    }

    /** Класс отказа, по которому адаптер выбирает статус ответа. */
    public Failure failure() {
        return failure;
    }

    /** Сообщение с ключом, которое переводится на язык запроса. */
    public Message reason() {
        return reason;
    }

    /** Дополнительные поля ответа об ошибке. */
    public Map<String, Object> details() {
        return details;
    }
}
