package uz.salvadore.hamkorbank.archi.interchange.application;

import java.util.Map;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Отказ сценария interchange. Классы отказа — те же, что у modeling: адаптер REST
 * выводит из них коды ответа одинаково для обоих контекстов.
 */
public class InterchangeException extends RuntimeException {

    private final String code;
    private final Failure failure;
    private final Map<String, Object> details;

    public InterchangeException(String code, Failure failure, String message, Map<String, Object> details) {
        super(code + ": " + message);
        this.code = Objects.requireNonNull(code, "code");
        this.failure = Objects.requireNonNull(failure, "failure");
        this.details = Map.copyOf(details);
    }

    public String code() {
        return code;
    }

    public Failure failure() {
        return failure;
    }

    public Map<String, Object> details() {
        return details;
    }
}
