package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/**
 * Сохранить модель как версию.
 *
 * @param comment        комментарий; {@code null} — без комментария
 * @param idempotencyKey ключ повтора; {@code null} — без идемпотентности
 */
public record SaveVersionCommand(UUID modelId, String comment, String idempotencyKey) {
}
