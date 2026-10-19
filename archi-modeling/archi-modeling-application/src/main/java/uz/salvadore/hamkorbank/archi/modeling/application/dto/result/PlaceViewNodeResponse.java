package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

/**
 * Итог размещения узла.
 *
 * @param created ложно, если элемент уже стоял на представлении и вернулся его узел
 */
public record PlaceViewNodeResponse(ViewNodeResponse node, boolean created) {
}
