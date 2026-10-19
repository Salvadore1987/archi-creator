package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.List;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;

/**
 * Поставить на представление ребро существующей связи.
 *
 * @param sourceId узел или ребро представления, изображающее источник связи
 * @param targetId узел или ребро представления, изображающее цель связи
 * @param id       ключ, выбранный клиентом; {@code null} — выдаёт сервер
 * @param archiId  идентификатор Archi, выбранный клиентом; {@code null} — выдаёт сервер
 */
public record PlaceViewEdgeCommand(UUID viewId, UUID relationshipId, UUID sourceId, UUID targetId,
                                   List<BendpointValue> bendpoints, UUID id, String archiId) {

    public PlaceViewEdgeCommand {
        bendpoints = List.copyOf(bendpoints);
    }
}
