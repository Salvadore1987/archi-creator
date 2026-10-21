package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.application.ExportDefectException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportModelQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ExportFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.CatalogCsvOptions;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.CatalogCsvWriter;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.DeterministicZip;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportDelivery;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportFileNames;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.LossReportJson;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.ExportDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefSchema;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefWriter;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.Artifact;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportFormat;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportOptions;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.FileSlug;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossEntry;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossReport;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetVersionSnapshotQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionSnapshotResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Выгрузка модели по зафиксированной версии: {@code .archimate}, каталог CSV и Open Exchange.
 *
 * <p>Файл версии — её снимок, собранный из базы при сохранении детерминированным
 * писателем. Выгрузка строится из него, а не из текущего состояния таблиц: правка,
 * идущая параллельно, в файл не попадает. У {@code .archimate} и CSV отчёт о потерях пуст,
 * у Open Exchange он едет в архиве рядом с файлом.
 *
 * <p>Каждый формат — своя операция со своей метрикой. Имя формата сценарий только ищет
 * в таблице до проверки роли; неизвестное имя отклоняется уже после неё — под операцией
 * выгрузки {@code .archimate}, которой адрес обслуживается по умолчанию. Так без роли
 * клиент получает {@code 403}, а не отказ в значении параметра.
 */
public final class ModelExportQueryHandler {

    /** Формат, если клиент его не задал, — файл модели. */
    private static final String DEFAULT_FORMAT = "archimate";
    private static final String ARCHIMATE_MEDIA_TYPE = "application/xml";
    private static final Map<String, InterchangeOperation> OPERATIONS = Map.of(
            "archimate", InterchangeOperation.EXPORT_ARCHIMATE,
            "csv", InterchangeOperation.EXPORT_CATALOG_CSV,
            "oef", InterchangeOperation.EXPORT_OPEN_EXCHANGE);

    private final InterchangeKernel kernel;
    private final ModelExchangeApplicationService versions;
    private final Notation notation;
    private final OefSchema oefSchema;
    private final ExportDelivery delivery;

    /**
     * @param notation  размеры фигур по умолчанию — те же, что у канвы и картинок
     * @param oefSchema официальная схема OEF; одна на приложение, компилируется при старте
     */
    public ModelExportQueryHandler(InterchangeKernel kernel, ModelExchangeApplicationService versions,
                                   Notation notation, OefSchema oefSchema) {
        this.kernel = kernel;
        this.versions = versions;
        this.notation = notation;
        this.oefSchema = oefSchema;
        this.delivery = new ExportDelivery(kernel.events(), kernel.metrics(), kernel.clock(), kernel.uuids());
    }

    /** Файл модели в формате запроса. */
    public ExportFileResponse handle(EditorIdentity actor, ExportModelQuery query) {
        String format = query.format() == null ? DEFAULT_FORMAT : query.format();
        Optional<InterchangeOperation> known = Optional.ofNullable(OPERATIONS.get(format));
        return kernel.run(known.orElse(InterchangeOperation.EXPORT_ARCHIMATE), actor, () -> {
            InterchangeOperation operation = known.orElseThrow(() -> new InterchangeException(
                    InterchangeCodes.UNKNOWN_FORMAT, Failure.UNPROCESSABLE,
                    Message.of(InterchangeMessages.UNKNOWN_FORMAT, format), Map.of("format", format)));
            return switch (operation) {
                case EXPORT_CATALOG_CSV -> catalogCsv(actor, query,
                        CatalogCsvOptions.of(query.separator(), query.folderArchiId()));
                case EXPORT_OPEN_EXCHANGE -> openExchange(actor, query);
                default -> archimate(actor, query);
            };
        });
    }

    /** Файл {@code .archimate} — снимок версии как есть. */
    private ExportFileResponse archimate(EditorIdentity actor, ExportModelQuery query) {
        VersionSnapshotResponse snapshot = snapshot(actor, query);
        return deliver(actor, snapshot, ExportFormat.ARCHIMATE, ExportOptions.none(),
                Artifact.of(snapshot.xml(), ARCHIMATE_MEDIA_TYPE, fileName(snapshot, "archimate")),
                LossReport.lossless());
    }

    /**
     * Каталог в CSV — из той же зафиксированной версии, что и файл модели: таблица и {@code .archimate}
     * одной версии не расходятся. Доступно {@code VIEWER}: читатель забирает ландшафт, не получая прав на правку.
     */
    private ExportFileResponse catalogCsv(EditorIdentity actor, ExportModelQuery query, CatalogCsvOptions options) {
        VersionSnapshotResponse snapshot = snapshot(actor, query);
        byte[] zip = new CatalogCsvWriter(kernel.texts()).write(snapshot.xml(), options);
        ExportOptions exportOptions = new ExportOptions(null, null, null, String.valueOf(options.separator()),
                options.folderArchiId());
        return deliver(actor, snapshot, ExportFormat.CSV_CATALOG, exportOptions,
                Artifact.of(zip, "application/zip", fileName(snapshot, "csv.zip")), LossReport.lossless());
    }

    /**
     * Open Exchange Format: zip из файла и {@code loss-report.json}. Имя файла в архиве — слаг в латинице:
     * распаковщики, не читающие флаг UTF-8 в zip (системный {@code unzip} macOS, старый Проводник), не создают
     * файл с кириллицей. Результат проверяется по официальной схеме до отдачи; не прошёл — файл не отдаётся,
     * а клиент получает {@code IXC_OEF_SCHEMA_VIOLATION} как ошибку сервера.
     */
    private ExportFileResponse openExchange(EditorIdentity actor, ExportModelQuery query) {
        VersionSnapshotResponse snapshot = snapshot(actor, query);
        OefWriter.Written written = new OefWriter(notation)
                .write(new StaxArchiDocumentReader().read(new ByteArrayInputStream(snapshot.xml())));
        Optional<String> violation = oefSchema.violation(written.xml());
        if (violation.isPresent()) {
            throw new ExportDefectException(InterchangeCodes.OEF_SCHEMA_VIOLATION,
                    Message.of(InterchangeMessages.OEF_SCHEMA_VIOLATION), violation.get());
        }
        List<LossEntry> entries = kernel.lossEntries(written.losses());
        String inner = FileSlug.slug(snapshot.model().name()) + "-v" + snapshot.versionNo() + ".xml";
        byte[] zip = new DeterministicZip().add(inner, written.xml())
                .add(LossReportJson.FILE_NAME, LossReportJson.report(ExportFormat.OPEN_EXCHANGE.name(),
                        snapshot.versionNo(), entries).getBytes(StandardCharsets.UTF_8))
                .bytes();
        return deliver(actor, snapshot, ExportFormat.OPEN_EXCHANGE, ExportOptions.none(),
                Artifact.of(zip, "application/zip", fileName(snapshot, "oef.zip")), new LossReport(entries, true));
    }

    private VersionSnapshotResponse snapshot(EditorIdentity actor, ExportModelQuery query) {
        return versions.getVersionSnapshot(actor, new GetVersionSnapshotQuery(query.modelId(), query.versionNo()));
    }

    private ExportFileResponse deliver(EditorIdentity actor, VersionSnapshotResponse snapshot, ExportFormat format,
                                       ExportOptions options, Artifact artifact, LossReport report) {
        delivery.deliver(actor, snapshot, null, format, options, artifact, report);
        return ExportDataMapper.file(artifact, snapshot.versionNo(), report.entries().size());
    }

    private static String fileName(VersionSnapshotResponse snapshot, String extension) {
        return ExportFileNames.safe(snapshot.model().name(), "model") + "-v" + snapshot.versionNo() + "." + extension;
    }
}
