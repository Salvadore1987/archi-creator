package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Элемент модели.
 *
 * @param layer     слой ArchiMate: {@code BUSINESS}, {@code APPLICATION}, …
 * @param supported ложно у типа, который редактор не правит
 */
public record ElementResponse(UUID id, UUID folderId, String archiId, String archiType, String layer, String name,
                              @Nullable String documentation, List<PropertyResponse> properties, boolean supported,
                              long sortOrder) {

    public ElementResponse {
        properties = List.copyOf(properties);
    }
}
