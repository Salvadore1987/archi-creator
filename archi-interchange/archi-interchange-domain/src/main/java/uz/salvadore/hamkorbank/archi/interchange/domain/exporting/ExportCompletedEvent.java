package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ExportJobId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Выгрузка завершена. Публикуется и при пустом отчёте о потерях — отсутствие потерь тоже факт.
 */
public record ExportCompletedEvent(ExportJobId exportJobId, WorkspaceId workspaceId, ModelId modelId,
                              Optional<ViewId> viewId, ExportFormat format, long sourceVersionNo,
                              ContentHash artifactHash, long artifactSize, int lossEntryCount, Instant occurredAt)
        implements ExportJobEvent {

    public ExportCompletedEvent {
        Objects.requireNonNull(exportJobId, "exportJobId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(artifactHash, "artifactHash");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** Событие о доставленном задании; до доставки его нет. */
    public static ExportCompletedEvent of(ExportJob job, Instant now) {
        Artifact artifact = job.artifact().orElseThrow(
                () -> new IllegalStateException(Message.of(InterchangeMessages.EXPORT_WITHOUT_ARTIFACT).toString()));
        return new ExportCompletedEvent(job.id(), job.workspaceId(), job.modelId(), job.viewId(), job.format(),
                job.sourceVersionNo(), artifact.hash(), artifact.bytes().length,
                job.lossReport().map(r -> r.entries().size()).orElse(0), now);
    }
}
