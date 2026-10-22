package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Снимок версии для выгрузки.
 *
 * @param versionNo номер версии; {@code null} — последняя
 */
public record GetVersionSnapshotQuery(UUID modelId, @Nullable Long versionNo) {
}
