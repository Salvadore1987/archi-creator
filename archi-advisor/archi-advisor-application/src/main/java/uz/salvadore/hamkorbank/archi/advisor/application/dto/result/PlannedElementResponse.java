package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

/**
 * Элемент плана.
 *
 * @param existingRef такой элемент уже есть в модели; {@code null} — нет
 */
public record PlannedElementResponse(String archiType, String name, ElementRefResponse existingRef) {
}
