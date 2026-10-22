package uz.salvadore.hamkorbank.archi.interchange.application.dto.command;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Импорт файла {@code .archimate} — как его прислал клиент. Что подставить вместо пропущенного,
 * решает сценарий.
 *
 * @param workspaceId    пространство; {@code null} — не задано, берётся пространство по умолчанию
 * @param fileName       имя файла; {@code null} или пустое — имя по умолчанию
 * @param content        содержимое файла
 * @param idempotencyKey ключ идемпотентности; {@code null} или пустой — свой на каждую попытку
 */
public record ImportArchimateFileCommand(@Nullable UUID workspaceId, @Nullable String fileName, byte[] content,
                                         @Nullable String idempotencyKey) {

    public ImportArchimateFileCommand {
        Objects.requireNonNull(content, "content");
    }
}
