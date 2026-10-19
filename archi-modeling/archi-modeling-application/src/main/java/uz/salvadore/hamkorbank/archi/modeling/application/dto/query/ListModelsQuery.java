package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.UUID;

/**
 * Модели пространства, которые автор видит.
 *
 * @param workspaceId пространство; {@code null} — первое
 */
public record ListModelsQuery(UUID workspaceId) {
}
