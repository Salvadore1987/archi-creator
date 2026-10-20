package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportAllViewImagesQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ExportFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.DeterministicZip;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportDelivery;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportFileNames;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.LossReportJson;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.RasterGate;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.ExportDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Rasterizer;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.Artifact;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.FileSlug;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossEntry;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossReport;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetVersionSnapshotQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionSnapshotResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Все представления модели одним архивом из одного снимка: файл {@code <слаг>.<8 hex>.<ext>}
 * на представление и {@code loss-report.json}.
 */
public final class AllViewImagesExportQueryHandler {

    private final InterchangeKernel kernel;
    private final ModelExchangeApplicationService versions;
    private final ViewImages images;
    private final ExportDelivery delivery;

    public AllViewImagesExportQueryHandler(InterchangeKernel kernel, ModelExchangeApplicationService versions,
                                           Notation notation, Typesetter typesetter, Rasterizer rasterizer,
                                           RasterGate gate) {
        this.kernel = kernel;
        this.versions = versions;
        this.images = new ViewImages(kernel, notation, typesetter, rasterizer, gate);
        this.delivery = new ExportDelivery(kernel.events(), kernel.metrics(), kernel.clock(), kernel.uuids());
    }

    /** Архив картинок по запросу; параметры разбираются после проверки роли. */
    public ExportFileResponse handle(EditorIdentity actor, ExportAllViewImagesQuery query) {
        return kernel.run(InterchangeOperation.EXPORT_ALL_VIEW_IMAGES, actor, () -> {
            ViewImages.Settings settings = ViewImages.settings(query.format(), query.scale(), query.outline(),
                    query.background());
            VersionSnapshotResponse snapshot = versions.getVersionSnapshot(actor,
                    new GetVersionSnapshotQuery(query.modelId(), query.versionNo()));
            ModelDocument document = ViewImages.read(snapshot);
            List<LossEntry> all = new ArrayList<>();
            List<LossReportJson.ViewLosses> report = new ArrayList<>();
            DeterministicZip zip = new DeterministicZip();
            Set<String> names = new HashSet<>();
            for (DocumentNode view : document.views()) {
                ViewImages.Picture picture = images.picture(document, view, settings);
                String name = view.attribute("name").orElse("");
                String file = unique(names, FileSlug.of(name, view.archiId()), settings.extension());
                zip.add(file, picture.bytes());
                List<LossEntry> entries = images.entries(picture.losses());
                all.addAll(entries);
                report.add(new LossReportJson.ViewLosses(view.archiId().value(), name, file, entries));
            }
            zip.add(LossReportJson.FILE_NAME, LossReportJson.viewsReport(settings.format().name(),
                    snapshot.versionNo(), report).getBytes(StandardCharsets.UTF_8));
            Artifact artifact = Artifact.of(zip.bytes(), "application/zip",
                    ExportFileNames.safe(snapshot.model().name(), "model") + "-v" + snapshot.versionNo()
                            + "-" + settings.extension() + ".zip");
            delivery.deliver(actor, snapshot, null, settings.format(), settings.options(), artifact,
                    new LossReport(all, true));
            return ExportDataMapper.file(artifact, snapshot.versionNo(), all.size());
        });
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
