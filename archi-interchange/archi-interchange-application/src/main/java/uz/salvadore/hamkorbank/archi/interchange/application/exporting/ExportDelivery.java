package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.time.Clock;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.Artifact;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportFormat;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportJob;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportOptions;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossReport;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ExportJobId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionSnapshotResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Задание выгрузки от запроса до доставки: файл собран из снимка версии, задание
 * проходит {@code REQUESTED → RENDERED → DELIVERED}, событие доставки уходит
 * подписчикам, число потерь — в метрику формата.
 */
final class ExportDelivery {

    private final InterchangeEvents events;
    private final InterchangeMetrics metrics;
    private final Clock clock;
    private final UuidV7 uuids;

    ExportDelivery(InterchangeEvents events, InterchangeMetrics metrics, Clock clock, UuidV7 uuids) {
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
        this.uuids = uuids;
    }

    /**
     * Доставляет файл, собранный из снимка.
     *
     * @param viewId {@code null} — выгрузка модели или пакет всех представлений
     */
    void deliver(EditorIdentity actor, VersionSnapshotResponse snapshot, ViewId viewId, ExportFormat format,
                 ExportOptions options, Artifact artifact, LossReport report) {
        ExportJob job = ExportJob.request(ExportJobId.next(uuids), new WorkspaceId(snapshot.model().workspaceId()),
                new ModelId(snapshot.model().id()), viewId, format, options, snapshot.versionNo(),
                actor.subject(), clock.instant());
        job.render(snapshot.versionNo(), artifact, report);
        job.deliver(clock.instant());
        events.publish(job.pullEvents());
        metrics.exportLosses(format.name(), report.entries().size());
    }
}
