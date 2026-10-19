package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.List;
import java.util.UUID;

/**
 * Представление целиком: заголовок, узлы и рёбра в порядке документа.
 *
 * @param viewpoint точка зрения; {@code null} — не задана
 * @param editable  ложно у представления, которое редактор не правит
 */
public record ViewResponse(UUID id, UUID modelId, UUID folderId, String archiId, String archiType, String name,
                           String documentation, String viewpoint, List<PropertyResponse> properties,
                           boolean editable, List<ViewNodeResponse> nodes, List<ViewEdgeResponse> edges) {

    public ViewResponse {
        properties = List.copyOf(properties);
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
