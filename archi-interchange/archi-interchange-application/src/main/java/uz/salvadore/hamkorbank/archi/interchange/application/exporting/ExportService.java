package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.Artifact;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportCompleted;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportFormat;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportJob;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportOptions;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossReport;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ExportJobId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionSnapshot;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * UC-IXC-002: экспорт модели в {@code .archimate} по зафиксированной версии (INV-IXC-008).
 *
 * <p>Файл версии — её снимок, собранный из базы при сохранении детерминированным
 * писателем (INV-IXC-004). Выгрузка отдаёт его, а не текущее состояние таблиц: правка,
 * идущая параллельно, в файл не попадает. Формат без потерь — отчёт о потерях пуст
 * (INV-IXC-006).
 */
public final class ExportService {

    public static final String ARCHIMATE_MEDIA_TYPE = "application/xml";

    private final VersionService versions;
    private final InterchangeEvents events;
    private final InterchangeMetrics metrics;
    private final Clock clock;
    private final UuidV7 uuids;

    public ExportService(VersionService versions, InterchangeEvents events, InterchangeMetrics metrics, Clock clock) {
        this.versions = versions;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
        this.uuids = new UuidV7(clock);
    }

    /** Результат выгрузки: файл и версия, из которой он собран. */
    public record Export(Artifact artifact, long versionNo) {
    }

    public Export archimate(EditorIdentity actor, UUID modelId, Optional<Long> versionNo) {
        InterchangeOperation operation = InterchangeOperation.EXPORT_ARCHIMATE;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            VersionSnapshot snapshot = versions.snapshot(actor,
                    uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId.of(modelId), versionNo);
            return deliver(actor, snapshot, ExportFormat.ARCHIMATE, ExportOptions.none(),
                    Artifact.of(snapshot.xml(), ARCHIMATE_MEDIA_TYPE, fileName(snapshot, "archimate")));
        });
    }

    private Export deliver(EditorIdentity actor, VersionSnapshot snapshot, ExportFormat format, ExportOptions options,
                           Artifact artifact) {
        ExportJob job = ExportJob.request(ExportJobId.next(uuids), new WorkspaceId(snapshot.model().workspaceId().value()),
                new ModelId(snapshot.model().id().value()), Optional.empty(), format, options, snapshot.versionNo(),
                actor.subject(), clock.instant());
        job.render(snapshot.versionNo(), artifact, LossReport.lossless());
        job.deliver();
        events.publish(List.of(ExportCompleted.of(job, clock.instant())));
        return new Export(artifact, snapshot.versionNo());
    }

    private static String fileName(VersionSnapshot snapshot, String extension) {
        String base = snapshot.model().name().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        return (base.isEmpty() ? "model" : base) + "-v" + snapshot.versionNo() + "." + extension;
    }
}
