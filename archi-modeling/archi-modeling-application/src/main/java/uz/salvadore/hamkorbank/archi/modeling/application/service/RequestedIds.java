package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.UUID;

/**
 * Идентификаторы нового объекта, заданные клиентом. Клиент, который копит правки у себя
 * и отправляет их позже, ссылается на объект до ответа сервера — поэтому ключ и
 * {@code archiId} он выбирает сам. {@code null} — сервер выдаёт их как обычно.
 *
 * @param id      внутренний ключ строки; {@code null} — не задан
 * @param archiId идентификатор в файле Archi, строкой: формат проверяет сценарий; {@code null} — не задан
 */
public record RequestedIds(UUID id, String archiId) {

    /** Клиент идентификаторов не выбирал. */
    public static final RequestedIds NONE = new RequestedIds(null, null);

    /** Идентификаторы из запроса; {@code null} — не заданы. */
    public static RequestedIds of(UUID id, String archiId) {
        return new RequestedIds(id, archiId);
    }
}
