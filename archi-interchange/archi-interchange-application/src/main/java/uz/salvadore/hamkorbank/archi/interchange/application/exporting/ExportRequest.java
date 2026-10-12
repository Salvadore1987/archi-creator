package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.util.Objects;
import java.util.Optional;

/**
 * Запрос выгрузки как его подал клиент: формат по имени из контракта API
 * ({@code archimate}, {@code csv}, {@code oef}), версия и параметры каталога.
 * Что из этого допустимо и какой выгрузкой обслуживается — решает
 * {@link ExportService}, а не адаптер, принявший запрос.
 *
 * @param separator     только для каталога CSV: {@code ;}, {@code ,} или {@code tab}
 * @param folderArchiId только для каталога CSV: поддерево папки
 */
public record ExportRequest(String format, Optional<Long> versionNo, Optional<String> separator,
                            Optional<String> folderArchiId) {

    public ExportRequest {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(versionNo, "versionNo");
        Objects.requireNonNull(separator, "separator");
        Objects.requireNonNull(folderArchiId, "folderArchiId");
    }
}
