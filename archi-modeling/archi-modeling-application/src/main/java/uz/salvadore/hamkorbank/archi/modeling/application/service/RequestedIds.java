package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Идентификаторы нового объекта, заданные клиентом. Клиент, который копит правки у себя
 * и отправляет их позже, ссылается на объект до ответа сервера — поэтому ключ и
 * {@code archiId} он выбирает сам. Пусто — сервер выдаёт их как обычно.
 *
 * @param id      внутренний ключ строки
 * @param archiId идентификатор в файле Archi, строкой: формат проверяет сценарий
 */
public record RequestedIds(Optional<UUID> id, Optional<String> archiId) {

    public static final RequestedIds NONE = new RequestedIds(Optional.empty(), Optional.empty());

    public RequestedIds {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archiId, "archiId");
    }

    public static RequestedIds of(UUID id, String archiId) {
        return new RequestedIds(Optional.ofNullable(id), Optional.ofNullable(archiId));
    }
}
