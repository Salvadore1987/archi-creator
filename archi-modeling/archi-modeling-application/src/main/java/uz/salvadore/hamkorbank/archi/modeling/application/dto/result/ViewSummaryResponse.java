package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.UUID;

/** Представление в дереве модели — без узлов и рёбер. */
public record ViewSummaryResponse(UUID id, UUID folderId, String archiId, String archiType, String name,
                                  long sortOrder) {
}
