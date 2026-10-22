package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Создать элемент.
 *
 * @param archiType      тип ArchiMate, как его прислал клиент
 * @param folderId       папка; {@code null} — корень слоя типа
 * @param id             ключ, выбранный клиентом; {@code null} — выдаёт сервер
 * @param archiId        идентификатор Archi, выбранный клиентом; {@code null} — выдаёт сервер
 * @param idempotencyKey ключ повтора; {@code null} — без идемпотентности
 */
public record CreateElementCommand(UUID modelId, String archiType, String name, @Nullable UUID folderId,
                                   @Nullable UUID id, @Nullable String archiId, @Nullable String idempotencyKey) {
}
