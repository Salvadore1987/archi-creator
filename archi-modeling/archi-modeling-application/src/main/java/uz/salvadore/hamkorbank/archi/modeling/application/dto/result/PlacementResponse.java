package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.List;
import java.util.UUID;

/** Что размещено на одном представлении: элементы с узлом и связи с ребром, без повторов. */
public record PlacementResponse(UUID viewId, List<UUID> elementIds, List<UUID> relationshipIds) {

    public PlacementResponse {
        elementIds = List.copyOf(elementIds);
        relationshipIds = List.copyOf(relationshipIds);
    }
}
