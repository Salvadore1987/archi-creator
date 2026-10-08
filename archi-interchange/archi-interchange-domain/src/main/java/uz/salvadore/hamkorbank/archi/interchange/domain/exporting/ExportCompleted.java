package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ExportJobId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Выгрузка завершена. Публикуется и при пустом отчёте о потерях — отсутствие потерь тоже факт.
 */
public record ExportCompleted(ExportJobId exportJobId, WorkspaceId workspaceId, ModelId modelId,
                              Optional<ViewId> viewId, ExportFormat format, long sourceVersionNo,
                              ContentHash artifactHash, long artifactSize, int lossEntryCount, Instant occurredAt) {

    public ExportCompleted {
        Objects.requireNonNull(exportJobId, "exportJobId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(artifactHash, "artifactHash");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** Событие о доставленном задании; до доставки его нет. */
    public static ExportCompleted of(ExportJob job, Instant now) {
        Artifact artifact = job.artifact().orElseThrow(() -> new IllegalStateException("задание без артефакта"));
        return new ExportCompleted(job.id(), job.workspaceId(), job.modelId(), job.viewId(), job.format(),
                job.sourceVersionNo(), artifact.hash(), artifact.bytes().length,
                job.lossReport().map(r -> r.entries().size()).orElse(0), now);
    }
}
