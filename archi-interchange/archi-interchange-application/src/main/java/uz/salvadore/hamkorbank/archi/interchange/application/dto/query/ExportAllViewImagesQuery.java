package uz.salvadore.hamkorbank.archi.interchange.application.dto.query;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Картинки всех представлений модели одним архивом — как их запросил клиент. Параметры —
 * как у картинки одного представления.
 *
 * @param versionNo номер версии; {@code null} — последняя
 */
public record ExportAllViewImagesQuery(UUID modelId, @Nullable String format, @Nullable Integer scale,
                                       @Nullable Boolean outline, @Nullable String background,
                                       @Nullable Long versionNo) {

    public ExportAllViewImagesQuery {
        Objects.requireNonNull(modelId, "modelId");
    }
}
