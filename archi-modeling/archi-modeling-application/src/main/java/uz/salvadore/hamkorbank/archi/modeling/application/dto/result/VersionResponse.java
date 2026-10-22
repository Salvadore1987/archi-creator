package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Версия модели без снимка.
 *
 * @param label             метка релиза; {@code null} — нет
 * @param snapshotAvailable снимок в базе не очищен
 * @param contentHash       SHA-256 снимка
 * @param gitSha            коммит публикации; {@code null} — не опубликована
 */
public record VersionResponse(long versionNo, String author, @Nullable String comment, @Nullable String label,
                              Instant createdAt, boolean snapshotAvailable, String contentHash,
                              @Nullable String gitSha) {
}
