package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportViewImageQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ExportFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportDelivery;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportFileNames;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.ExportDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Rasterizer;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.Artifact;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossReport;
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
 * Картинка одного представления из зафиксированной версии. Представление ищется в снимке
 * по своему {@code archiId}: его нет в запрошенной версии — отказ {@code IXC_VIEW_NOT_IN_VERSION}.
 */
public final class ViewImageExportQueryHandler {

    private final InterchangeKernel kernel;
    private final ViewQueryApplicationService queries;
    private final ModelExchangeApplicationService versions;
    private final ViewImages images;
    private final ExportDelivery delivery;

    public ViewImageExportQueryHandler(InterchangeKernel kernel, ViewQueryApplicationService queries,
                                       ModelExchangeApplicationService versions, Notation notation,
                                       Typesetter typesetter, Rasterizer rasterizer) {
        this.kernel = kernel;
        this.queries = queries;
        this.versions = versions;
        this.images = new ViewImages(kernel, notation, typesetter, rasterizer);
        this.delivery = new ExportDelivery(kernel.events(), kernel.metrics(), kernel.clock(), kernel.uuids());
    }

    /** Картинка представления по запросу; параметры разбираются после проверки роли. */
    public ExportFileResponse handle(EditorIdentity actor, ExportViewImageQuery query) {
        return kernel.run(InterchangeOperation.EXPORT_VIEW_IMAGE, actor, () -> {
            ViewImages.Settings settings = ViewImages.settings(query.format(), query.scale(), query.outline(),
                    query.background());
            ViewResponse view = queries.getView(actor, new GetViewQuery(query.viewId()));
            VersionSnapshotResponse snapshot = versions.getVersionSnapshot(actor,
                    new GetVersionSnapshotQuery(view.modelId(), query.versionNo()));
            ModelDocument document = ViewImages.read(snapshot);
            DocumentNode node = document.find(ArchiId.of(view.archiId()))
                    .filter(n -> n.tag().equals("element"))
                    .orElseThrow(() -> new InterchangeException(InterchangeCodes.VIEW_NOT_IN_VERSION,
                            Failure.NOT_FOUND,
                            Message.of(InterchangeMessages.IMAGE_VIEW_NOT_IN_VERSION, snapshot.versionNo()),
                            Map.of("viewId", query.viewId(), "versionNo", snapshot.versionNo())));
            ViewImages.Picture picture = images.picture(document, node, settings);
            String name = node.attribute("name").orElse("") + "-v" + snapshot.versionNo();
            Artifact artifact = Artifact.of(picture.bytes(), settings.mediaType(),
                    ExportFileNames.safe(name, "view") + "." + settings.extension());
            LossReport report = new LossReport(images.entries(picture.losses()), true);
            delivery.deliver(actor, snapshot, new ViewId(query.viewId()), settings.format(), settings.options(),
                    artifact, report);
            return ExportDataMapper.file(artifact, snapshot.versionNo(), report.entries().size());
        });
    }
}
