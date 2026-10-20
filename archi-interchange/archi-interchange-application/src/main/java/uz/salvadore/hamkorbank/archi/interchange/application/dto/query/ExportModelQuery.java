package uz.salvadore.hamkorbank.archi.interchange.application.dto.query;

import java.util.Objects;
import java.util.UUID;

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
public record ExportModelQuery(UUID modelId, String format, Long versionNo, String separator, String folderArchiId) {

    public ExportModelQuery {
        Objects.requireNonNull(modelId, "modelId");
    }
}
