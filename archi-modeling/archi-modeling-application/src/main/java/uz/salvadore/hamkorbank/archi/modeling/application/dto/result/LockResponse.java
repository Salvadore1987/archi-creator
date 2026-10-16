package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Блокировка модели на запись.
 *
 * @param owner subject владельца
 */
public record LockResponse(UUID modelId, String owner, Instant acquiredAt, Instant expiresAt) {
}
