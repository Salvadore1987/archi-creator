package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Создать представление.
 *
 * @param folderId       папка; {@code null} — корень представлений
 * @param id             ключ, выбранный клиентом; {@code null} — выдаёт сервер
 * @param archiId        идентификатор Archi, выбранный клиентом; {@code null} — выдаёт сервер
 * @param idempotencyKey ключ повтора; {@code null} — без идемпотентности
 */
public record CreateViewCommand(UUID modelId, String name, @Nullable UUID folderId, @Nullable UUID id,
                                @Nullable String archiId, @Nullable String idempotencyKey) {
}
