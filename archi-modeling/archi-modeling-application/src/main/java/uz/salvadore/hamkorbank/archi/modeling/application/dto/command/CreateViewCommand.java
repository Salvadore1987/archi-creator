package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/**
 * Создать представление.
 *
 * @param folderId       папка; {@code null} — корень представлений
 * @param id             ключ, выбранный клиентом; {@code null} — выдаёт сервер
 * @param archiId        идентификатор Archi, выбранный клиентом; {@code null} — выдаёт сервер
 * @param idempotencyKey ключ повтора; {@code null} — без идемпотентности
 */
public record CreateViewCommand(UUID modelId, String name, UUID folderId, UUID id, String archiId,
                                String idempotencyKey) {
}
