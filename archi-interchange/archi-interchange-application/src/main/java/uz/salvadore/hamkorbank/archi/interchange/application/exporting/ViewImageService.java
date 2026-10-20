package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Rasterizer;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.ImageLoss;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.SvgWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.Artifact;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportFormat;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportOptions;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.FileSlug;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossEntry;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossReport;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.LossEntryId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetVersionSnapshotQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetViewQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionSnapshotResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Картинки представлений в SVG и PNG из зафиксированной версии.
 *
 * <p>Картинка строится из снимка версии, а не из текущих таблиц: правка после
 * сохранения в неё не попадает, а пакет всех представлений не смешивает версии.
 * PNG растеризуется из того же SVG с подписями в кривых. У каждой выгрузки есть
 * отчёт о потерях — пустой или нет.
 */
public final class ViewImageService {

    /** Картинка в SVG. */
    public static final String FORMAT_SVG = "svg";
    /** Картинка в PNG, растеризованная из SVG. */
    public static final String FORMAT_PNG = "png";
    /** Фон цвета бумаги, как на холсте. */
    public static final String BACKGROUND_PAPER = "paper";
    /** Прозрачный фон. */
    public static final String BACKGROUND_TRANSPARENT = "transparent";

    private static final Map<String, ExportFormat> FORMATS = Map.of(FORMAT_SVG, ExportFormat.SVG,
            FORMAT_PNG, ExportFormat.PNG);
    private static final Set<Integer> SCALES = Set.of(1, 2, 4);
    private static final List<Integer> SCALES_DESCENDING = List.of(4, 2, 1);
    /** Предел стороны PNG: 4 000 логических единиц при плотности 4. */
    public static final int MAX_PNG_SIDE = 16_000;

    private final ViewQueryApplicationService queries;
    private final ModelExchangeApplicationService versions;
    private final Notation notation;
    private final Typesetter typesetter;
    private final Rasterizer rasterizer;
    private final InterchangeMetrics metrics;
    private final TextCatalog texts;
    private final UuidV7 uuids;
    private final ExportDelivery delivery;

    public ViewImageService(ViewQueryApplicationService queries, ModelExchangeApplicationService versions,
                            Notation notation, Typesetter typesetter, Rasterizer rasterizer, InterchangeEvents events,
                            InterchangeMetrics metrics, TextCatalog texts, Clock clock) {
        this.queries = queries;
        this.versions = versions;
        this.notation = notation;
        this.typesetter = typesetter;
        this.rasterizer = rasterizer;
        this.metrics = metrics;
        this.texts = texts;
        this.uuids = new UuidV7(clock);
        this.delivery = new ExportDelivery(events, metrics, clock, uuids);
    }

    /** Файл картинки, версия, из которой он собран, и число записей отчёта о потерях. */
    public record ImageExport(Artifact artifact, long versionNo, int lossCount) {
    }

    /** Параметры после проверки: формат, плотность, кривые, прозрачный фон. */
    private record Settings(ExportFormat format, int scale, boolean outline, boolean transparent) {

        String extension() {
            return format == ExportFormat.PNG ? FORMAT_PNG : FORMAT_SVG;
        }

        ExportOptions options() {
            return new ExportOptions(BigDecimal.valueOf(scale), outline,
                    transparent ? BACKGROUND_TRANSPARENT : BACKGROUND_PAPER, null, null);
        }
    }

    private record Picture(byte[] bytes, List<ImageLoss> losses) {
    }

    /**
     * Одно представление. Представление ищется в снимке по своему {@code archiId}: его
     * нет в запрошенной версии — отказ {@code IXC_VIEW_NOT_IN_VERSION}.
     */
    public ImageExport viewImage(EditorIdentity actor, UUID viewId, ImageRequest request) {
        InterchangeOperation operation = InterchangeOperation.EXPORT_VIEW_IMAGE;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            Settings settings = settings(request);
            ViewResponse view = queries.getView(actor, new GetViewQuery(viewId));
            VersionSnapshotResponse snapshot = versions.getVersionSnapshot(actor,
                    new GetVersionSnapshotQuery(view.modelId(), request.versionNo()));
            ModelDocument document = read(snapshot);
            DocumentNode node = document.find(ArchiId.of(view.archiId()))
                    .filter(n -> n.tag().equals("element"))
                    .orElseThrow(() -> new InterchangeException(InterchangeCodes.VIEW_NOT_IN_VERSION,
                            Failure.NOT_FOUND,
                            Message.of(InterchangeMessages.IMAGE_VIEW_NOT_IN_VERSION, snapshot.versionNo()),
                            Map.of("viewId", viewId, "versionNo", snapshot.versionNo())));
            Picture picture = picture(document, node, settings);
            String name = node.attribute("name").orElse("") + "-v" + snapshot.versionNo();
            Artifact artifact = Artifact.of(picture.bytes(), mediaType(settings),
                    ExportFileNames.safe(name, "view") + "." + settings.extension());
            LossReport report = new LossReport(entries(picture.losses()), true);
            delivery.deliver(actor, snapshot, new ViewId(viewId), settings.format(), settings.options(), artifact,
                    report);
            return new ImageExport(artifact, snapshot.versionNo(), report.entries().size());
        });
    }

    /**
     * Все представления модели одним архивом из одного снимка: файл
     * {@code <слаг>.<8 hex>.<ext>} на представление и {@code loss-report.json}.
     */
    public ImageExport allViewImages(EditorIdentity actor, UUID modelId, ImageRequest request) {
        InterchangeOperation operation = InterchangeOperation.EXPORT_ALL_VIEW_IMAGES;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            Settings settings = settings(request);
            VersionSnapshotResponse snapshot = versions.getVersionSnapshot(actor,
                    new GetVersionSnapshotQuery(modelId, request.versionNo()));
            ModelDocument document = read(snapshot);
            List<LossEntry> all = new ArrayList<>();
            List<LossReportJson.ViewLosses> report = new ArrayList<>();
            DeterministicZip zip = new DeterministicZip();
            Set<String> names = new HashSet<>();
            for (DocumentNode view : document.views()) {
                Picture picture = picture(document, view, settings);
                String name = view.attribute("name").orElse("");
                String file = unique(names, FileSlug.of(name, view.archiId()), settings.extension());
                zip.add(file, picture.bytes());
                List<LossEntry> entries = entries(picture.losses());
                all.addAll(entries);
                report.add(new LossReportJson.ViewLosses(view.archiId().value(), name, file, entries));
            }
            zip.add(LossReportJson.FILE_NAME, LossReportJson.viewsReport(settings.format().name(),
                    snapshot.versionNo(), report).getBytes(StandardCharsets.UTF_8));
            Artifact artifact = Artifact.of(zip.bytes(), "application/zip",
                    ExportFileNames.safe(snapshot.model().name(), "model") + "-v" + snapshot.versionNo()
                            + "-" + settings.extension() + ".zip");
            LossReport lossReport = new LossReport(all, true);
            delivery.deliver(actor, snapshot, null, settings.format(), settings.options(), artifact, lossReport);
            return new ImageExport(artifact, snapshot.versionNo(), all.size());
        });
    }

    /**
     * Неизвестный формат — {@code IXC_UNKNOWN_FORMAT}; плотность и фон вне списка —
     * {@code IXC_INVALID_EXPORT_OPTION}.
     */
    private static Settings settings(ImageRequest request) {
        String formatName = Objects.requireNonNullElse(request.format(), FORMAT_SVG);
        ExportFormat format = Optional.ofNullable(FORMATS.get(formatName))
                .orElseThrow(() -> new InterchangeException(InterchangeCodes.UNKNOWN_FORMAT, Failure.UNPROCESSABLE,
                        Message.of(InterchangeMessages.UNKNOWN_FORMAT, formatName), Map.of("format", formatName)));
        int scale = Objects.requireNonNullElse(request.scale(), 1);
        if (!SCALES.contains(scale)) {
            throw invalidOption("scale", String.valueOf(scale));
        }
        String background = Objects.requireNonNullElse(request.background(), BACKGROUND_PAPER);
        if (!background.equals(BACKGROUND_PAPER) && !background.equals(BACKGROUND_TRANSPARENT)) {
            throw invalidOption("background", background);
        }
        return new Settings(format, scale, Objects.requireNonNullElse(request.outline(), false),
                background.equals(BACKGROUND_TRANSPARENT));
    }

    private static InterchangeException invalidOption(String parameter, String value) {
        return new InterchangeException(InterchangeCodes.INVALID_EXPORT_OPTION, Failure.UNPROCESSABLE,
                Message.of(InterchangeMessages.IMAGE_OPTION_INVALID, parameter, value),
                Map.of("parameter", parameter, "value", value));
    }

    /** PNG растеризуется из варианта в кривых: растеризатору не нужны шрифты. */
    private Picture picture(ModelDocument document, DocumentNode view, Settings settings) {
        SvgWriter writer = new SvgWriter(notation, typesetter, texts);
        boolean png = settings.format() == ExportFormat.PNG;
        SvgWriter.Rendered rendered = writer.write(document, view,
                new SvgWriter.Options(settings.outline() || png, settings.transparent()));
        if (!png) {
            return new Picture(rendered.svg(), rendered.losses());
        }
        int width = (int) Math.ceil(rendered.width() * settings.scale());
        int height = (int) Math.ceil(rendered.height() * settings.scale());
        if (width > MAX_PNG_SIDE || height > MAX_PNG_SIDE) {
            int maxScale = SCALES_DESCENDING.stream()
                    .filter(s -> rendered.width() * s <= MAX_PNG_SIDE && rendered.height() * s <= MAX_PNG_SIDE)
                    .findFirst().orElse(0);
            throw new InterchangeException(InterchangeCodes.IMAGE_TOO_LARGE, Failure.UNPROCESSABLE,
                    Message.of(InterchangeMessages.IMAGE_TOO_LARGE, width, height, MAX_PNG_SIDE),
                    Map.of("maxScale", maxScale, "viewArchiId", view.archiId().value()));
        }
        return new Picture(rasterizer.png(rendered.svg(), width, height), rendered.losses());
    }

    private List<LossEntry> entries(List<ImageLoss> losses) {
        return losses.stream().map(l -> new LossEntry(LossEntryId.next(uuids), ArchiId.of(l.archiId()),
                l.objectKind(), l.aspect(), texts.text(l.reason()))).toList();
    }

    private static ModelDocument read(VersionSnapshotResponse snapshot) {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(snapshot.xml()));
    }

    private static String mediaType(Settings settings) {
        return settings.format() == ExportFormat.PNG ? "image/png" : "image/svg+xml";
    }

    /** Совпадение коротких идентификаторов у двух представлений — второе получает суффикс. */
    private static String unique(Set<String> names, String base, String extension) {
        String name = base + "." + extension;
        for (int i = 2; !names.add(name); i++) {
            name = base + "-" + i + "." + extension;
        }
        return name;
    }
}
