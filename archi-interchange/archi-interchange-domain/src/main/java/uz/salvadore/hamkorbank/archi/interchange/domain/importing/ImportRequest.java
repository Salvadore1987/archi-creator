package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Что подано на импорт: откуда, чьё, с каким ключом и в каком режиме.
 *
 * @param targetModelId пусто — импорт новой модели
 * @param strictMode    {@code strict_import} рабочего пространства на момент подачи (FR-49)
 */
public record ImportRequest(WorkspaceId workspaceId, Optional<ModelId> targetModelId, String sourceName,
                            ContentHash sourceHash, long sourceSize, String idempotencyKey,
                            boolean strictMode, String startedBy) {

    public ImportRequest {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(targetModelId, "targetModelId");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(startedBy, "startedBy");
        if (sourceName.isBlank() || sourceName.length() > 500) {
            throw new IllegalArgumentException("имя файла пусто или длиннее 500 символов");
        }
        if (sourceSize <= 0) {
            throw new IllegalArgumentException("пустой файл не импортируется");
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("ключ идемпотентности пуст или длиннее 64 символов");
        }
    }

    /** Отпечаток и размер считаются по самому содержимому, а не принимаются на веру. */
    public static ImportRequest of(WorkspaceId workspaceId, Optional<ModelId> targetModelId, String sourceName,
                                   byte[] content, String idempotencyKey, boolean strictMode, String startedBy) {
        return new ImportRequest(workspaceId, targetModelId, sourceName, ContentHash.of(content), content.length,
                idempotencyKey, strictMode, startedBy);
    }
}
