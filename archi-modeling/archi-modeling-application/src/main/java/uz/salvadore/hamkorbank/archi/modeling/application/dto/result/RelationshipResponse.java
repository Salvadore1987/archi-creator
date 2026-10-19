package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.List;
import java.util.UUID;

/**
 * Связь модели; концы — элементы или другие связи.
 *
 * @param accessType вид доступа у Access; {@code null} — у прочих
 * @param directed   направленность у Association; {@code null} — у прочих
 */
public record RelationshipResponse(UUID id, UUID folderId, String archiId, String archiType, UUID sourceId,
                                   UUID targetId, String name, String documentation, String accessType,
                                   Boolean directed, List<PropertyResponse> properties, boolean supported,
                                   long sortOrder) {

    public RelationshipResponse {
        properties = List.copyOf(properties);
    }
}
