package uz.salvadore.hamkorbank.archi.interchange.application.port;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Текст сообщения interchange на языке запроса: для ответа клиенту и для того, что
 * сервер сохраняет как данные — находки импорта, комментарий версии, значения CSV.
 */
public interface TextCatalog {

    String text(Message message);
}
