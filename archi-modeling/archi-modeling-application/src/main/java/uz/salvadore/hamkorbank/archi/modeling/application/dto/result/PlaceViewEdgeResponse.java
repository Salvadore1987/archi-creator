package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

/**
 * Итог размещения ребра.
 *
 * @param created ложно, если такое же ребро между теми же концами уже было
 */
public record PlaceViewEdgeResponse(ViewEdgeResponse edge, boolean created) {
}
