package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import uz.salvadore.hamkorbank.archi.interchange.application.ExportDefectException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefLoss;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefSchema;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefWriter;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.Artifact;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportCompletedEvent;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportFormat;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportJob;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportOptions;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.FileSlug;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossEntry;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossReport;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ExportJobId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.LossEntryId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionSnapshot;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Экспорт модели по зафиксированной версии: {@code .archimate}, каталог CSV и Open Exchange.
 *
 * <p>Файл версии — её снимок, собранный из базы при сохранении детерминированным
 * писателем. Выгрузка строится из него, а не из текущего состояния таблиц: правка,
 * идущая параллельно, в файл не попадает. У {@code .archimate} отчёт о потерях пуст,
 * у Open Exchange он едет в архиве рядом с файлом.
 */
public final class ExportService {

    /** Тип содержимого файла {@code .archimate} в ответе. */
    public static final String ARCHIMATE_MEDIA_TYPE = "application/xml";
    /** Имя файла отчёта о потерях внутри архива выгрузки. */
    public static final String LOSS_REPORT_FILE = "loss-report.json";
    private static final long FIXED_ENTRY_TIME = 0L;

    /** Имена форматов в запросе — как в контракте API; формат по умолчанию — файл модели. */
    public static final String FORMAT_ARCHIMATE = "archimate";
    /** Каталог элементов и связей в CSV. */
    public static final String FORMAT_CSV = "csv";
    /** Файл Open Exchange Format с отчётом о потерях. */
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
    private final Notation notation;
    private final OefSchema oefSchema;

    /**
     * @param notation  размеры фигур по умолчанию — те же, что у канвы и картинок
     * @param oefSchema официальная схема OEF; одна на приложение, компилируется при старте
     */
    public ExportService(VersionService versions, InterchangeEvents events, InterchangeMetrics metrics,
                         TextCatalog texts, Notation notation, OefSchema oefSchema, Clock clock) {
        this.texts = texts;
        this.notation = notation;
        this.oefSchema = oefSchema;
        this.versions = versions;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
        this.uuids = new UuidV7(clock);
    }

    /** Результат выгрузки: файл, версия, из которой он собран, и число записей отчёта о потерях. */
    public record Export(Artifact artifact, long versionNo, int lossCount) {
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
            case OPEN_EXCHANGE -> openExchange(actor, modelId, request.versionNo());
            default -> throw new InterchangeException(InterchangeCodes.FORMAT_NOT_AVAILABLE, Failure.UNPROCESSABLE,
                    Message.of(InterchangeMessages.FORMAT_NOT_AVAILABLE), Map.of("format", request.format()));
        };
    }

    /** Файл {@code .archimate} зафиксированной версии модели; без номера — последней. */
    public Export archimate(EditorIdentity actor, UUID modelId, Optional<Long> versionNo) {
        InterchangeOperation operation = InterchangeOperation.EXPORT_ARCHIMATE;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            VersionSnapshot snapshot = versions.snapshot(actor,
                    uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId.of(modelId), versionNo.orElse(null));
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
                    uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId.of(modelId), versionNo.orElse(null));
            byte[] zip = new CatalogCsvWriter(texts).write(snapshot.xml(), options);
            ExportOptions exportOptions = new ExportOptions(Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(String.valueOf(options.separator())), options.folderArchiId());
            return deliver(actor, snapshot, ExportFormat.CSV_CATALOG, exportOptions,
                    Artifact.of(zip, "application/zip", fileName(snapshot, "csv.zip")));
        });
    }

    /**
     * Open Exchange Format из той же версии: zip из файла и {@code loss-report.json}.
     * Имя файла в архиве — слаг в латинице: распаковщики, не читающие флаг UTF-8 в zip
     * (системный {@code unzip} macOS, старый Проводник), не создают файл с кириллицей.
     * Результат проверяется по официальной схеме до отдачи; не прошёл — файл не отдаётся,
     * выгрузка — сбой, а клиент получает {@code IXC_OEF_SCHEMA_VIOLATION} как ошибку сервера.
     */
    public Export openExchange(EditorIdentity actor, UUID modelId, Optional<Long> versionNo) {
        InterchangeOperation operation = InterchangeOperation.EXPORT_OPEN_EXCHANGE;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            VersionSnapshot snapshot = versions.snapshot(actor,
                    uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId.of(modelId), versionNo.orElse(null));
            OefWriter.Written written = new OefWriter(notation)
                    .write(new StaxArchiDocumentReader().read(new ByteArrayInputStream(snapshot.xml())));
            ExportJob job = job(actor, snapshot, ExportFormat.OPEN_EXCHANGE, ExportOptions.none());
            Optional<String> violation = oefSchema.violation(written.xml());
            if (violation.isPresent()) {
                job.fail(violation.get());
                throw new ExportDefectException(InterchangeCodes.OEF_SCHEMA_VIOLATION,
                        Message.of(InterchangeMessages.OEF_SCHEMA_VIOLATION), violation.get());
            }
            List<LossEntry> entries = written.losses().stream().map(this::entry).toList();
            String inner = FileSlug.slug(snapshot.model().name()) + "-v" + snapshot.versionNo() + ".xml";
            byte[] zip = zip(inner, written.xml(), LossReportJson.report(ExportFormat.OPEN_EXCHANGE.name(),
                    snapshot.versionNo(), entries).getBytes(StandardCharsets.UTF_8));
            return deliver(job, snapshot, Artifact.of(zip, "application/zip", fileName(snapshot, "oef.zip")),
                    new LossReport(entries, true));
        });
    }

    private Export deliver(EditorIdentity actor, VersionSnapshot snapshot, ExportFormat format, ExportOptions options,
                           Artifact artifact) {
        return deliver(job(actor, snapshot, format, options), snapshot, artifact, LossReport.lossless());
    }

    private Export deliver(ExportJob job, VersionSnapshot snapshot, Artifact artifact, LossReport report) {
        job.render(snapshot.versionNo(), artifact, report);
        job.deliver();
        events.publish(List.of(ExportCompletedEvent.of(job, clock.instant())));
        metrics.exportLosses(job.format().name(), report.entries().size());
        return new Export(artifact, snapshot.versionNo(), report.entries().size());
    }

    private ExportJob job(EditorIdentity actor, VersionSnapshot snapshot, ExportFormat format, ExportOptions options) {
        return ExportJob.request(ExportJobId.next(uuids), new WorkspaceId(snapshot.model().workspaceId().value()),
                new ModelId(snapshot.model().id().value()), Optional.empty(), format, options, snapshot.versionNo(),
                actor.subject(), clock.instant());
    }

    private LossEntry entry(OefLoss loss) {
        return new LossEntry(LossEntryId.next(uuids), ArchiId.of(loss.archiId()), loss.objectKind(), loss.aspect(),
                texts.text(loss.reason()));
    }

    /** Архив детерминирован: время записей фиксировано, порядок — файл, затем отчёт. */
    private static byte[] zip(String fileName, byte[] file, byte[] report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry
                    : List.of(Map.entry(fileName, file), Map.entry(LOSS_REPORT_FILE, report))) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(FIXED_ENTRY_TIME);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static String fileName(VersionSnapshot snapshot, String extension) {
        return safeFileName(snapshot.model().name(), "model") + "-v" + snapshot.versionNo() + "." + extension;
    }

    /** Имя без символов, недопустимых в имени файла; пустое — {@code fallback}. */
    static String safeFileName(String name, String fallback) {
        String base = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        return base.isEmpty() ? fallback : base;
    }
}
