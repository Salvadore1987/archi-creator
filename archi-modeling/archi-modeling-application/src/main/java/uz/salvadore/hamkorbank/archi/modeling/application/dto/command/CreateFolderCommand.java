package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Создать папку в дереве модели.
 *
 * @param id             ключ, выбранный клиентом; {@code null} — выдаёт сервер
 * @param archiId        идентификатор Archi, выбранный клиентом; {@code null} — выдаёт сервер
 * @param idempotencyKey ключ повтора; {@code null} — без идемпотентности
 */
public record CreateFolderCommand(UUID modelId, UUID parentId, String name, @Nullable UUID id, @Nullable String archiId,
                                  @Nullable String idempotencyKey) {
}
