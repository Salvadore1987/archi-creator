package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

/**
 * Сколько объектов одного вида добавлено, удалено и изменено.
 *
 * @param kind {@code MODEL}, {@code FOLDER}, {@code ELEMENT}, {@code RELATIONSHIP} или {@code VIEW}
 */
public record GitChangeCountResponse(String kind, int added, int removed, int changed) {
}
