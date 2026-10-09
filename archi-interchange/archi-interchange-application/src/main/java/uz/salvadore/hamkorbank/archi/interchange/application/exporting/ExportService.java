package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
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
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Экспорт модели в {@code .archimate} по зафиксированной версии.
 *
 * <p>Файл версии — её снимок, собранный из базы при сохранении детерминированным
 * писателем. Выгрузка отдаёт его, а не текущее состояние таблиц: правка,
 * идущая параллельно, в файл не попадает. Формат без потерь — отчёт о потерях пуст.
 */
public final class ExportService {

    public static final String ARCHIMATE_MEDIA_TYPE = "application/xml";

    /** Имена форматов в запросе — как в контракте API; формат по умолчанию — файл модели. */
    public static final String FORMAT_ARCHIMATE = "archimate";
    public static final String FORMAT_CSV = "csv";
    public static final String FORMAT_OEF = "oef";

    private static final Map<String, ExportFormat> FORMATS = Map.of(
            FORMAT_ARCHIMATE, ExportFormat.ARCHIMATE,
            FORMAT_CSV, ExportFormat.CSV_CATALOG,
            FORMAT_OEF, ExportFormat.OPEN_EXCHANGE);

    private final VersionService versions;
    private final InterchangeEvents events;
    private final InterchangeMetrics metrics;
    private final Clock clock;
    private final UuidV7 uuids;
    private final TextCatalog texts;

    public ExportService(VersionService versions, InterchangeEvents events, InterchangeMetrics metrics, TextCatalog texts,
                         Clock clock) {
        this.texts = texts;
        this.versions = versions;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
        this.uuids = new UuidV7(clock);
    }

    /** Результат выгрузки: файл и версия, из которой он собран. */
    public record Export(Artifact artifact, long versionNo) {
    }

    /**
     * Выгрузка по запросу клиента. Здесь решается, какой формат поддержан и чем он
     * обслуживается: неизвестное имя — отказ {@code IXC_UNKNOWN_FORMAT}, известный, но ещё
     * не реализованный формат — {@code IXC_FORMAT_NOT_AVAILABLE}. Оба — до чтения модели.
     */
    public Export export(EditorIdentity actor, UUID modelId, ExportRequest request) {
        ExportFormat format = Optional.ofNullable(FORMATS.get(request.format()))
                .orElseThrow(() -> new InterchangeException(InterchangeCodes.UNKNOWN_FORMAT, Failure.UNPROCESSABLE,
                        Message.of(InterchangeMessages.UNKNOWN_FORMAT, request.format()),
                        Map.of("format", request.format())));
        return switch (format) {
            case ARCHIMATE -> archimate(actor, modelId, request.versionNo());
            case CSV_CATALOG -> catalogCsv(actor, modelId, request.versionNo(),
                    CatalogCsvOptions.of(request.separator(), request.folderArchiId()));
            default -> throw new InterchangeException(InterchangeCodes.FORMAT_NOT_AVAILABLE, Failure.UNPROCESSABLE,
                    Message.of(InterchangeMessages.FORMAT_NOT_AVAILABLE), Map.of("format", request.format()));
        };
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

    /**
     * Каталог в CSV — из той же зафиксированной версии, что и файл модели:
     * таблица и {@code .archimate} одной версии не расходятся. Доступно
     * {@code VIEWER}: читатель забирает ландшафт, не получая прав на правку.
     */
    public Export catalogCsv(EditorIdentity actor, UUID modelId, Optional<Long> versionNo, CatalogCsvOptions options) {
        InterchangeOperation operation = InterchangeOperation.EXPORT_CATALOG_CSV;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            VersionSnapshot snapshot = versions.snapshot(actor,
                    uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId.of(modelId), versionNo);
            byte[] zip = new CatalogCsvWriter(texts).write(snapshot.xml(), options);
            ExportOptions exportOptions = new ExportOptions(Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(String.valueOf(options.separator())), options.folderArchiId());
            return deliver(actor, snapshot, ExportFormat.CSV_CATALOG, exportOptions,
                    Artifact.of(zip, "application/zip", fileName(snapshot, "csv.zip")));
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
        metrics.exportLosses(format.name(), job.lossReport().map(r -> r.entries().size()).orElse(0));
        return new Export(artifact, snapshot.versionNo());
    }

    private static String fileName(VersionSnapshot snapshot, String extension) {
        String base = snapshot.model().name().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        return (base.isEmpty() ? "model" : base) + "-v" + snapshot.versionNo() + "." + extension;
    }
}
