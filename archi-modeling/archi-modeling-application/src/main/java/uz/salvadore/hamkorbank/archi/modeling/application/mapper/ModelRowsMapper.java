package uz.salvadore.hamkorbank.archi.modeling.application.mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.ModelRows;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationshipType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ConceptRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.StyleOverride;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEndpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Строки модели — в агрегаты и обратно. Агрегаты собираются фабриками импорта: модель проверяет
 * свою структуру, представление — ссылки на модель. Поддержан ли тип элемента или связи, решает каталог
 * метамодели здесь же, при сборке, — строка этого не несёт.
 */
public final class ModelRowsMapper {

    private static final ArchiTypeRegistry REGISTRY = ArchiTypeRegistry.archimate32();

    private ModelRowsMapper() {
    }

    /**
     * Чья модель получится из строк: ключ, пространство, автор и время. Импорт даёт новые,
     * замена содержимого — те же, что у модели, чьё содержимое заменяется.
     */
    public record Identity(ModelId modelId, WorkspaceId workspaceId, String createdBy, Instant createdAt,
                           Instant updatedAt, long version) {
    }

    /**
     * Агрегаты из строк. Недостающие корневые папки модель дописывает сама — ключами и идентификаторами
     * из {@code folderIds} и {@code archiIds}.
     */
    public static ModelContent content(ModelRows rows, Identity identity, Supplier<FolderId> folderIds,
                                       Supplier<ArchiId> archiIds) {
        Set<UUID> relationshipIds = new HashSet<>();
        rows.relationships().forEach(r -> relationshipIds.add(r.id()));
        Set<UUID> edgeIds = new HashSet<>();
        rows.views().forEach(v -> v.edges().forEach(e -> edgeIds.add(e.id())));
        List<ViewHeader> headers = rows.views().stream().map(v -> viewHeader(v, identity.modelId())).toList();
        ArchitectureModel model = ArchitectureModel.imported(header(rows.header(), identity),
                rows.folders().stream().map(ModelRowsMapper::folder).toList(),
                rows.elements().stream().map(ModelRowsMapper::element).toList(),
                rows.relationships().stream().map(r -> relationship(r, relationshipIds)).toList(),
                headers.stream().map(h -> new ViewRef(h.id(), h.folderId(), h.archiId(), h.archiType(), h.name(),
                        h.sortOrder())).toList(),
                folderIds, archiIds);
        List<View> views = new ArrayList<>();
        for (int i = 0; i < rows.views().size(); i++) {
            ModelRows.View row = rows.views().get(i);
            views.add(View.imported(headers.get(i), row.nodes().stream().map(ModelRowsMapper::node).toList(),
                    row.edges().stream().map(e -> edge(e, edgeIds)).toList(), model));
        }
        return new ModelContent(model, views);
    }

    /** Строки из агрегатов — в порядке, в котором их отдаёт хранилище. */
    public static ModelRows rows(ModelContent content) {
        ArchitectureModel model = content.model();
        return new ModelRows(header(model.header()),
                model.folders().values().stream().map(ModelRowsMapper::folder).toList(),
                model.elements().values().stream().map(ModelRowsMapper::element).toList(),
                model.relationships().values().stream().map(ModelRowsMapper::relationship).toList(),
                content.views().stream().map(ModelRowsMapper::view).toList());
    }

    // ── Строки ↔ агрегаты: перегрузки двух направлений рядом ─────────

    private static ModelHeader header(ModelRows.Header row, Identity identity) {
        String version = row.archiVersion() != null && ModelHeader.validArchiVersion(row.archiVersion())
                ? row.archiVersion() : ModelHeader.DEFAULT_ARCHI_VERSION;
        return new ModelHeader(identity.modelId(), identity.workspaceId(), ArchiId.of(row.archiId()), row.name(),
                row.documentation(), version, ModelStatus.ACTIVE, entries(row.properties()), rawXml(row.rawXml()),
                identity.createdBy(), identity.createdAt(), identity.updatedAt(), identity.version());
    }

    private static ModelRows.Header header(ModelHeader header) {
        return new ModelRows.Header(header.archiId().value(), header.name(), header.documentation(),
                header.archiVersion(), propertyRows(header.properties()), rawXml(header.rawXml()));
    }

    private static ModelFolder folder(ModelRows.Folder row) {
        return new ModelFolder(FolderId.of(row.id()), row.parentId() == null ? null : FolderId.of(row.parentId()),
                ArchiId.of(row.archiId()), row.name(),
                row.type() == null ? null : FolderType.fromFileValue(row.type()).orElse(null),
                SortOrder.of(row.sortOrder()), rawXml(row.rawXml()));
    }

    private static ModelRows.Folder folder(ModelFolder folder) {
        return new ModelRows.Folder(folder.id().value(), folder.parentId() == null ? null : folder.parentId().value(),
                folder.archiId().value(), folder.name(),
                folder.folderType() == null ? null : folder.folderType().fileValue(), folder.sortOrder().value(),
                rawXml(folder.rawXml()));
    }

    private static Element element(ModelRows.Element row) {
        ArchiType type = ArchiType.of(row.archiType());
        boolean supported = REGISTRY.find(type)
                .filter(c -> c.kind() == ConceptKind.ELEMENT || c.kind() == ConceptKind.JUNCTION)
                .map(c -> c.supported()).orElse(false);
        return new Element(ElementId.of(row.id()), FolderId.of(row.folderId()), ArchiId.of(row.archiId()), type,
                row.name(), row.documentation(), entries(row.properties()), SortOrder.of(row.sortOrder()),
                supported, rawXml(row.rawXml()));
    }

    private static ModelRows.Element element(Element element) {
        return new ModelRows.Element(element.id().value(), element.folderId().value(), element.archiId().value(),
                element.archiType().value(), element.name(), element.documentation(),
                propertyRows(element.properties()), element.sortOrder().value(), rawXml(element.rawXml()));
    }

    private static Relationship relationship(ModelRows.Relationship row, Set<UUID> relationshipIds) {
        ArchiType type = ArchiType.of(row.archiType());
        AccessType accessType = row.accessType() == null ? null : AccessType.fromFileValue(row.accessType())
                .orElseThrow(() -> new InvalidValueException(ModelingMessages.ROW_ACCESS_TYPE, row.accessType()));
        return new Relationship(RelationshipId.of(row.id()), FolderId.of(row.folderId()), ArchiId.of(row.archiId()),
                type, concept(row.sourceId(), relationshipIds), concept(row.targetId(), relationshipIds), row.name(),
                row.documentation(), accessType, row.directed(), entries(row.properties()),
                SortOrder.of(row.sortOrder()), RelationshipType.fromArchiType(type).isPresent(),
                rawXml(row.rawXml()));
    }

    private static ModelRows.Relationship relationship(Relationship relationship) {
        return new ModelRows.Relationship(relationship.id().value(), relationship.folderId().value(),
                relationship.archiId().value(), relationship.archiType().value(), relationship.source().value(),
                relationship.target().value(), relationship.name(), relationship.documentation(),
                relationship.accessType() == null ? null : String.valueOf(relationship.accessType().fileValue()),
                relationship.directed(), propertyRows(relationship.properties()), relationship.sortOrder().value(),
                rawXml(relationship.rawXml()));
    }

    private static ConceptRef concept(UUID id, Set<UUID> relationshipIds) {
        return relationshipIds.contains(id) ? RelationshipId.of(id) : ElementId.of(id);
    }

    private static ViewHeader viewHeader(ModelRows.View row, ModelId modelId) {
        return new ViewHeader(ViewId.of(row.id()), modelId, FolderId.of(row.folderId()), ArchiId.of(row.archiId()),
                DiagramType.of(row.archiType()), row.name(), row.documentation(), row.viewpoint(),
                entries(row.properties()), SortOrder.of(row.sortOrder()), rawXml(row.rawXml()), 0);
    }

    private static ViewNode node(ModelRows.Node row) {
        ModelRows.Box box = row.bounds();
        Bounds bounds = box == null ? new Bounds(0, 0, Bounds.DEFAULT_SIZE, Bounds.DEFAULT_SIZE)
                : new Bounds(box.x(), box.y(), box.width(), box.height());
        return new ViewNode(ViewNodeId.of(row.id()), row.parentId() == null ? null : ViewNodeId.of(row.parentId()),
                ArchiId.of(row.archiId()), DiagramType.of(row.archiType()),
                row.elementId() == null ? null : ElementId.of(row.elementId()), bounds, style(row.style()),
                row.label(), row.content(), SortOrder.of(row.sortOrder()), rawXml(row.rawXml()));
    }

    private static ModelRows.Node node(ViewNode node) {
        Bounds bounds = node.bounds();
        return new ModelRows.Node(node.id().value(), node.parentId() == null ? null : node.parentId().value(),
                node.archiId().value(), node.archiType().value(),
                node.elementId() == null ? null : node.elementId().value(),
                new ModelRows.Box(bounds.x(), bounds.y(), bounds.width(), bounds.height()), style(node.style()),
                node.label(), node.content(), node.sortOrder().value(), rawXml(node.rawXml()));
    }

    private static ViewEdge edge(ModelRows.Edge row, Set<UUID> edgeIds) {
        return new ViewEdge(ViewEdgeId.of(row.id()), ArchiId.of(row.archiId()), DiagramType.of(row.archiType()),
                row.relationshipId() == null ? null : RelationshipId.of(row.relationshipId()),
                endpoint(row.sourceId(), edgeIds), endpoint(row.targetId(), edgeIds),
                row.bendpoints().stream().map(b -> new Bendpoint(b.startX(), b.startY(), b.endX(), b.endY()))
                        .toList(),
                style(row.style()), SortOrder.of(row.sortOrder()), rawXml(row.rawXml()));
    }

    private static ModelRows.Edge edge(ViewEdge edge) {
        return new ModelRows.Edge(edge.id().value(), edge.archiId().value(), edge.archiType().value(),
                edge.relationshipId() == null ? null : edge.relationshipId().value(), edge.source().value(),
                edge.target().value(),
                edge.bendpoints().stream().map(b -> new BendpointValue(b.startX(), b.startY(), b.endX(), b.endY()))
                        .toList(),
                style(edge.style()), edge.sortOrder().value(), rawXml(edge.rawXml()));
    }

    private static ViewEndpoint endpoint(UUID id, Set<UUID> edgeIds) {
        return edgeIds.contains(id) ? ViewEdgeId.of(id) : ViewNodeId.of(id);
    }

    private static StyleOverride style(StyleValue style) {
        return style == null ? new StyleOverride(null, null, null, null, null)
                : new StyleOverride(style.fillColor(), style.font(), style.fontColor(), style.lineColor(),
                        style.textAlignment());
    }

    private static StyleValue style(StyleOverride style) {
        return new StyleValue(style.fillColor(), style.font(), style.fontColor(), style.lineColor(),
                style.textAlignment());
    }

    private static List<PropertyEntry> entries(List<ModelRows.Property> rows) {
        return rows.stream().map(p -> new PropertyEntry(p.key(), p.value(), SortOrder.of(p.sortOrder()))).toList();
    }

    private static RawXml rawXml(String value) {
        return value == null ? null : new RawXml(value);
    }

    private static String rawXml(RawXml raw) {
        return raw == null ? null : raw.value();
    }

    private static ModelRows.View view(View view) {
        ViewHeader header = view.header();
        return new ModelRows.View(header.id().value(), header.folderId().value(), header.archiId().value(),
                header.archiType().value(), header.name(), header.documentation(), header.viewpoint(),
                propertyRows(header.properties()), header.sortOrder().value(), rawXml(header.rawXml()),
                view.nodes().values().stream().map(ModelRowsMapper::node).toList(),
                view.edges().values().stream().map(ModelRowsMapper::edge).toList());
    }

    private static List<ModelRows.Property> propertyRows(List<PropertyEntry> properties) {
        return properties.stream().map(p -> new ModelRows.Property(p.key(), p.value(), p.sortOrder().value()))
                .toList();
    }
}
