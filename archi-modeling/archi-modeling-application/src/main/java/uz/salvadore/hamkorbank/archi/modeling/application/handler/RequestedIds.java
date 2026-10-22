package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Идентификаторы нового объекта, заданные клиентом. Клиент, который копит правки у себя
 * и отправляет их позже, ссылается на объект до ответа сервера — поэтому ключ и
 * {@code archiId} он выбирает сам. {@code null} — сервер выдаёт их как обычно.
 *
 * @param id      внутренний ключ строки; {@code null} — не задан
 * @param archiId идентификатор в файле Archi, строкой: формат проверяет сценарий; {@code null} — не задан
 */
record RequestedIds(@Nullable UUID id, @Nullable String archiId) {

    /** Клиент идентификаторов не выбирал. */
    static final RequestedIds NONE = new RequestedIds(null, null);

    /** Идентификаторы из запроса; {@code null} — не заданы. */
    static RequestedIds of(@Nullable UUID id, @Nullable String archiId) {
        return new RequestedIds(id, archiId);
    }
}
