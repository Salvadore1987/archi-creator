package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Что подано на импорт: откуда, чьё, с каким ключом и в каком режиме.
 *
 * @param targetModelId пусто — импорт новой модели
 * @param strictMode    {@code strict_import} рабочего пространства на момент подачи
 */
public record ImportRequest(WorkspaceId workspaceId, Optional<ModelId> targetModelId, String sourceName,
                            ContentHash sourceHash, long sourceSize, String idempotencyKey,
                            boolean strictMode, String startedBy) {

    public static final int SOURCE_NAME_MAX = 500;
    /** Имя файла, когда клиент его не передал: сессия импорта без имени источника не бывает. */
    public static final String DEFAULT_SOURCE_NAME = "model.archimate";
    public static final int IDEMPOTENCY_KEY_MAX = 64;

    public ImportRequest {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(targetModelId, "targetModelId");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(startedBy, "startedBy");
        if (sourceName.isBlank() || sourceName.length() > SOURCE_NAME_MAX) {
            throw new InvalidValueException(InterchangeMessages.SOURCE_NAME_INVALID, SOURCE_NAME_MAX);
        }
        if (sourceSize <= 0) {
            throw new InvalidValueException(InterchangeMessages.SOURCE_EMPTY);
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX) {
            throw new InvalidValueException(InterchangeMessages.IDEMPOTENCY_KEY_INVALID, IDEMPOTENCY_KEY_MAX);
        }
    }

    /** Отпечаток и размер считаются по самому содержимому, а не принимаются на веру. */
    /** Имя источника: переданное клиентом, а пустое или отсутствующее — имя по умолчанию. */
    public static String sourceNameOrDefault(Optional<String> fileName) {
        return fileName.filter(name -> !name.isBlank()).orElse(DEFAULT_SOURCE_NAME);
    }

    public static ImportRequest of(WorkspaceId workspaceId, Optional<ModelId> targetModelId, String sourceName,
                                   byte[] content, String idempotencyKey, boolean strictMode, String startedBy) {
        return new ImportRequest(workspaceId, targetModelId, sourceName, ContentHash.of(content), content.length,
                idempotencyKey, strictMode, startedBy);
    }
}
