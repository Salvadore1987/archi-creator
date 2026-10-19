package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.List;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;

/**
 * Ребро представления.
 *
 * @param relationshipId связь ребра; {@code null} — у соединения без связи
 * @param style          собственный стиль; {@code null} — нет
 */
public record ViewEdgeResponse(UUID id, String archiId, String archiType, UUID relationshipId, UUID sourceId,
                               UUID targetId, List<BendpointValue> bendpoints, StyleValue style, long sortOrder) {

    public ViewEdgeResponse {
        bendpoints = List.copyOf(bendpoints);
    }
}
