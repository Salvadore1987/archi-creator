package uz.salvadore.hamkorbank.archi.interchange.application.dto.query;

import java.util.Objects;
import java.util.UUID;

/**
 * Картинки всех представлений модели одним архивом — как их запросил клиент. Параметры —
 * как у картинки одного представления.
 *
 * @param versionNo номер версии; {@code null} — последняя
 */
public record ExportAllViewImagesQuery(UUID modelId, String format, Integer scale, Boolean outline,
                                       String background, Long versionNo) {

    public ExportAllViewImagesQuery {
        Objects.requireNonNull(modelId, "modelId");
    }
}
