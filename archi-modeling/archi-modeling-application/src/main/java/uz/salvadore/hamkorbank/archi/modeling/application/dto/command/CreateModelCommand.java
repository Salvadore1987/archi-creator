package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Создать пустую модель.
 *
 * @param workspaceId    пространство; {@code null} — первое, до мультиарендности оно одно
 * @param idempotencyKey ключ повтора; {@code null} — без идемпотентности
 */
public record CreateModelCommand(@Nullable UUID workspaceId, String name, @Nullable String idempotencyKey) {
}
