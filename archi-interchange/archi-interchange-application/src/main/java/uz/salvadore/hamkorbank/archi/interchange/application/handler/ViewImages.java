package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportLoss;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.RasterGate;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Rasterizer;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.SvgWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportFormat;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.ExportOptions;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionSnapshotResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Общее у сценариев картинок: разбор параметров запроса, отрисовка представления из снимка
 * и записи отчёта о потерях.
 *
 * <p>Картинка строится из снимка версии, а не из текущих таблиц: правка после
 * сохранения в неё не попадает, а пакет всех представлений не смешивает версии.
 * PNG растеризуется из того же SVG с подписями в кривых. У каждой выгрузки есть
 * отчёт о потерях — пустой или нет.
 */
final class ViewImages {

    private static final String FORMAT_SVG = "svg";
    private static final String FORMAT_PNG = "png";
    /** Фон цвета бумаги, как на холсте. */
    private static final String BACKGROUND_PAPER = "paper";
    private static final String BACKGROUND_TRANSPARENT = "transparent";
    private static final Map<String, ExportFormat> FORMATS = Map.of(FORMAT_SVG, ExportFormat.SVG,
            FORMAT_PNG, ExportFormat.PNG);
    private static final Set<Integer> SCALES = Set.of(1, 2, 4);
    private static final List<Integer> SCALES_DESCENDING = List.of(4, 2, 1);
    /** Предел стороны PNG: 4 000 логических единиц при плотности 4. */
    static final int MAX_PNG_SIDE = 16_000;
    private static final long MEGAPIXEL = 1_000_000;

    private final InterchangeKernel kernel;
    private final Notation notation;
    private final Typesetter typesetter;
    private final Rasterizer rasterizer;
    private final RasterGate gate;

    ViewImages(InterchangeKernel kernel, Notation notation, Typesetter typesetter, Rasterizer rasterizer,
               RasterGate gate) {
        this.kernel = kernel;
        this.notation = notation;
        this.typesetter = typesetter;
        this.rasterizer = rasterizer;
        this.gate = gate;
    }

    /** Параметры после проверки: формат, плотность, кривые, прозрачный фон. */
    record Settings(ExportFormat format, int scale, boolean outline, boolean transparent) {

        String extension() {
            return format == ExportFormat.PNG ? FORMAT_PNG : FORMAT_SVG;
        }

        String mediaType() {
            return format == ExportFormat.PNG ? "image/png" : "image/svg+xml";
        }

        ExportOptions options() {
            return new ExportOptions(BigDecimal.valueOf(scale), outline,
                    transparent ? BACKGROUND_TRANSPARENT : BACKGROUND_PAPER, null, null);
        }
    }

    /** Картинка и то, что в неё не перенеслось. */
    record Picture(byte[] bytes, List<ExportLoss> losses) {
    }

    /**
     * Параметры запроса; не заданное ({@code null}) — SVG, плотность 1, текст текстом, фон бумаги.
     * Неизвестный формат — {@code IXC_UNKNOWN_FORMAT}; плотность и фон вне списка —
     * {@code IXC_INVALID_EXPORT_OPTION}.
     */
    static Settings settings(String formatName, Integer scale, Boolean outline, String background) {
        String name = Objects.requireNonNullElse(formatName, FORMAT_SVG);
        ExportFormat format = Optional.ofNullable(FORMATS.get(name))
                .orElseThrow(() -> new InterchangeException(InterchangeCodes.UNKNOWN_FORMAT, Failure.UNPROCESSABLE,
                        Message.of(InterchangeMessages.UNKNOWN_FORMAT, name), Map.of("format", name)));
        int density = Objects.requireNonNullElse(scale, 1);
        if (!SCALES.contains(density)) {
            throw invalidOption("scale", String.valueOf(density));
        }
        String paper = Objects.requireNonNullElse(background, BACKGROUND_PAPER);
        if (!paper.equals(BACKGROUND_PAPER) && !paper.equals(BACKGROUND_TRANSPARENT)) {
            throw invalidOption("background", paper);
        }
        return new Settings(format, density, Objects.requireNonNullElse(outline, false),
                paper.equals(BACKGROUND_TRANSPARENT));
    }

    private static InterchangeException invalidOption(String parameter, String value) {
        return new InterchangeException(InterchangeCodes.INVALID_EXPORT_OPTION, Failure.UNPROCESSABLE,
                Message.of(InterchangeMessages.IMAGE_OPTION_INVALID, parameter, value),
                Map.of("parameter", parameter, "value", value));
    }

    /**
     * PNG растеризуется из варианта в кривых: растеризатору не нужны шрифты. Растр больше предела
     * по стороне или площади не строится; растеризация ждёт своей очереди.
     */
    Picture picture(ModelDocument document, DocumentNode view, Settings settings) {
        SvgWriter writer = new SvgWriter(notation, typesetter, kernel.texts());
        boolean png = settings.format() == ExportFormat.PNG;
        SvgWriter.Rendered rendered = writer.write(document, view,
                new SvgWriter.Options(settings.outline() || png, settings.transparent()));
        if (!png) {
            return new Picture(rendered.svg(), rendered.losses());
        }
        Raster raster = raster(rendered.width(), rendered.height(), settings.scale(), gate.maxAreaPx(),
                view.archiId().value());
        return new Picture(gate.through(() -> rasterizer.png(rendered.svg(), raster.width(), raster.height())),
                rendered.losses());
    }

    /** Размер растра в пикселях. */
    record Raster(int width, int height) {
    }

    /**
     * Растр картинки {@code width × height} логических единиц при плотности {@code scale}. Больше
     * {@link #MAX_PNG_SIDE} по стороне или {@code maxAreaPx} по площади — отказ
     * {@code IXC_IMAGE_TOO_LARGE} с наибольшей плотностью, при которой растр помещается
     * ({@code 0} — не помещается и при плотности 1).
     */
    static Raster raster(double width, double height, int scale, long maxAreaPx, String viewArchiId) {
        Raster raster = sized(width, height, scale);
        boolean sideFits = raster.width() <= MAX_PNG_SIDE && raster.height() <= MAX_PNG_SIDE;
        if (sideFits && fits(raster, maxAreaPx)) {
            return raster;
        }
        int maxScale = SCALES_DESCENDING.stream().filter(s -> fits(sized(width, height, s), maxAreaPx))
                .findFirst().orElse(0);
        Message reason = sideFits
                ? Message.of(InterchangeMessages.IMAGE_AREA_TOO_LARGE, raster.width(), raster.height(),
                        maxAreaPx / MEGAPIXEL)
                : Message.of(InterchangeMessages.IMAGE_TOO_LARGE, raster.width(), raster.height(), MAX_PNG_SIDE);
        throw new InterchangeException(InterchangeCodes.IMAGE_TOO_LARGE, Failure.UNPROCESSABLE, reason,
                Map.of("maxScale", maxScale, "viewArchiId", viewArchiId));
    }

    private static Raster sized(double width, double height, int scale) {
        return new Raster((int) Math.ceil(width * scale), (int) Math.ceil(height * scale));
    }

    private static boolean fits(Raster raster, long maxAreaPx) {
        return raster.width() <= MAX_PNG_SIDE && raster.height() <= MAX_PNG_SIDE
                && (long) raster.width() * raster.height() <= maxAreaPx;
    }

    /** Документ снимка версии. */
    static ModelDocument read(VersionSnapshotResponse snapshot) {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(snapshot.xml()));
    }
}
