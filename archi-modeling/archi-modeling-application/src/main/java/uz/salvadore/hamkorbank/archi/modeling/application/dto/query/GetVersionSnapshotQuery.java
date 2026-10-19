package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.UUID;

/**
 * Снимок версии для выгрузки.
 *
 * @param versionNo номер версии; {@code null} — последняя
 */
public record GetVersionSnapshotQuery(UUID modelId, Long versionNo) {
}
