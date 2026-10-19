package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

/**
 * Тип элемента в каталоге палитры.
 *
 * @param kind      элемент или соединитель
 * @param phase     фаза поддержки; {@code null} — вне фаз
 * @param supported тип правится в редакторе
 */
public record ElementTypeResponse(String archiType, String kind, String layer, String phase, boolean supported) {
}
