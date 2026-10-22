package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

import org.jspecify.annotations.Nullable;

/**
 * Элемент плана.
 *
 * @param existingRef такой элемент уже есть в модели; {@code null} — нет
 */
public record PlannedElementResponse(String archiType, String name, @Nullable ElementRefResponse existingRef) {
}
