package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.util.Objects;

/**
 * Запрос выгрузки как его подал клиент: формат по имени из контракта API
 * ({@code archimate}, {@code csv}, {@code oef}), версия и параметры каталога.
 * Что из этого допустимо и какой выгрузкой обслуживается — решает
 * {@link ExportService}, а не адаптер, принявший запрос.
 *
 * @param versionNo     номер версии; {@code null} — последняя
 * @param separator     только для каталога CSV: {@code ;}, {@code ,} или {@code tab}; {@code null} — не задан
 * @param folderArchiId только для каталога CSV: поддерево папки; {@code null} — вся модель
 */
public record ExportRequest(String format, Long versionNo, String separator, String folderArchiId) {

    public ExportRequest {
        Objects.requireNonNull(format, "format");
    }
}
