package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.time.Instant;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ExportJobId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Выгрузка завершена. Публикуется и при пустом отчёте о потерях — отсутствие потерь тоже факт.
 *
 * @param viewId представление — у выгрузки картинки; {@code null} — у выгрузки модели
 */
public record ExportJobCompletedEvent(ExportJobId exportJobId, WorkspaceId workspaceId, ModelId modelId,
                              ViewId viewId, ExportFormat format, long sourceVersionNo,
                              ContentHash artifactHash, long artifactSize, int lossEntryCount, Instant occurredAt)
        implements ExportJobEvent {

    public ExportJobCompletedEvent {
        Objects.requireNonNull(exportJobId, "exportJobId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(artifactHash, "artifactHash");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
