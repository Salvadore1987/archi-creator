package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/**
 * Создать пустую модель.
 *
 * @param workspaceId    пространство; {@code null} — первое, до мультиарендности оно одно
 * @param idempotencyKey ключ повтора; {@code null} — без идемпотентности
 */
public record CreateModelCommand(UUID workspaceId, String name, String idempotencyKey) {
}
