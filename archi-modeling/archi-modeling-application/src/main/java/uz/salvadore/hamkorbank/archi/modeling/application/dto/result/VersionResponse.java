package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.time.Instant;

/**
 * Версия модели без снимка.
 *
 * @param label             метка релиза; {@code null} — нет
 * @param snapshotAvailable снимок в базе не очищен
 * @param contentHash       SHA-256 снимка
 * @param gitSha            коммит публикации; {@code null} — не опубликована
 */
public record VersionResponse(long versionNo, String author, String comment, String label, Instant createdAt,
                              boolean snapshotAvailable, String contentHash, String gitSha) {
}
