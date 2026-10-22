package uz.salvadore.hamkorbank.archi.interchange.application.dto.query;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Выгрузка модели — как её запросил клиент. Какие значения допустимы и что подставить
 * вместо пропущенного, решает сценарий.
 *
 * @param format        имя формата из контракта API ({@code archimate}, {@code csv}, {@code oef});
 *                      {@code null} — не задан
 * @param versionNo     номер версии; {@code null} — последняя
 * @param separator     только для каталога CSV: разделитель; {@code null} — не задан
 * @param folderArchiId только для каталога CSV: поддерево папки; {@code null} или пустое — вся модель
 */
public record ExportModelQuery(UUID modelId, @Nullable String format, @Nullable Long versionNo,
                               @Nullable String separator, @Nullable String folderArchiId) {

    public ExportModelQuery {
        Objects.requireNonNull(modelId, "modelId");
    }
}
