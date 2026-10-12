package uz.salvadore.hamkorbank.archi.advisor.application.port;

/**
 * Внешний API не ответил, отказал или оборвал поток. Без текста: причина — в
 * {@code cause}, и в журнал она идёт без содержимого запроса.
 */
public final class LlmUnavailableException extends Exception {

    public LlmUnavailableException(Throwable cause) {
        super(cause);
    }
}
