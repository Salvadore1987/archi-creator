package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

import java.util.Objects;

/**
 * Файл выгрузки: содержимое и всё, что нужно ответу.
 *
 * @param contentHash отпечаток содержимого — для {@code ETag}
 * @param versionNo   версия, из снимка которой собран файл
 * @param lossCount   число записей отчёта о потерях; у {@code .archimate} — ноль
 */
public record ExportFileResponse(byte[] content, String mediaType, String fileName, String contentHash,
                                 long versionNo, int lossCount) {

    public ExportFileResponse {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(contentHash, "contentHash");
    }
}
