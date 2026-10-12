package uz.salvadore.hamkorbank.archi.advisor.application.port;

/**
 * Обращение к языковой модели. Открытие — синхронно: недоступность внешнего API
 * видна до открытия потока клиенту и отвечается честным отказом. Повтор после
 * первого полученного токена запрещён — пользователь уже видел часть ответа,
 * а повтор потратил бы токены заново.
 */
public interface LlmPort {

    /** Отправить запрос и дождаться начала ответа. */
    LlmStream open(LlmRequest request) throws LlmUnavailableException;
}
