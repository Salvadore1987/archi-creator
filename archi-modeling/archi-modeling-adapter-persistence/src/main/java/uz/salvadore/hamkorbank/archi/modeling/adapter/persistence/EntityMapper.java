package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ElementEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.FolderEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ModelEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.PropertyEmbeddable;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.RelationshipEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ViewEdgeEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ViewEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ViewNodeEntity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;
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
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEndpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Перевод между агрегатами и строками. Направления симметричны: {@code toX} строит
 * домен из строки, {@code apply} пишет домен в (новую или управляемую) строку.
 */
final class EntityMapper {

    private EntityMapper() {
    }

    // ── Модель ──────────────────────────────────────────────────────

    static ModelHeader toHeader(ModelEntity e) {
        return new ModelHeader(ModelId.of(e.id), WorkspaceId.of(e.workspaceId), ArchiId.of(e.archiId), e.name,
                Optional.ofNullable(e.documentation), e.archiVersion, ModelStatus.valueOf(e.status),
                toProperties(e.properties), raw(e.rawXml), e.createdBy, e.createdAt, e.updatedAt, e.version);
    }

    static void apply(ModelHeader h, ModelEntity e) {
        e.id = h.id().value();
        e.workspaceId = h.workspaceId().value();
        e.archiId = h.archiId().value();
        e.name = h.name();
        e.documentation = h.documentation().orElse(null);
        e.archiVersion = h.archiVersion();
        e.status = h.status().name();
        e.rawXml = h.rawXml().map(RawXml::value).orElse(null);
        e.createdBy = h.createdBy();
        e.createdAt = h.createdAt();
        e.updatedAt = h.updatedAt();
        replaceProperties(e.properties, h.properties());
    }

    // ── Папки, элементы, связи ──────────────────────────────────────

    static ModelFolder toFolder(FolderEntity e) {
        return new ModelFolder(FolderId.of(e.id), Optional.ofNullable(e.parentId).map(FolderId::of),
                ArchiId.of(e.archiId), e.name, Optional.ofNullable(e.folderType).map(FolderType::valueOf),
                SortOrder.of(e.sortOrder), raw(e.rawXml));
    }

    static void apply(ModelFolder f, UUID modelId, FolderEntity e) {
        e.id = f.id().value();
        e.modelId = modelId;
        e.parentId = f.parentId().map(FolderId::value).orElse(null);
        e.archiId = f.archiId().value();
        e.name = f.name();
        e.folderType = f.folderType().map(Enum::name).orElse(null);
        e.sortOrder = f.sortOrder().value();
        e.rawXml = f.rawXml().map(RawXml::value).orElse(null);
    }

    static Element toElement(ElementEntity e) {
        return new Element(ElementId.of(e.id), FolderId.of(e.folderId), ArchiId.of(e.archiId),
                ArchiType.of(e.archiType), e.name, Optional.ofNullable(e.documentation), toProperties(e.properties),
                SortOrder.of(e.sortOrder), e.supported, raw(e.rawXml));
    }

    static void apply(Element d, UUID modelId, ElementEntity e) {
        e.id = d.id().value();
        e.modelId = modelId;
        e.folderId = d.folderId().value();
        e.archiId = d.archiId().value();
        e.archiType = d.archiType().value();
        e.layer = d.layer().name();
        e.name = d.name();
        e.documentation = d.documentation().orElse(null);
        e.sortOrder = d.sortOrder().value();
        e.supported = d.supported();
        e.rawXml = d.rawXml().map(RawXml::value).orElse(null);
        replaceProperties(e.properties, d.properties());
    }

    static Relationship toRelationship(RelationshipEntity e) {
        return new Relationship(RelationshipId.of(e.id), FolderId.of(e.folderId), ArchiId.of(e.archiId),
                ArchiType.of(e.archiType), concept(e.sourceElementId, e.sourceRelationshipId),
                concept(e.targetElementId, e.targetRelationshipId), Optional.ofNullable(e.name),
                Optional.ofNullable(e.documentation), Optional.ofNullable(e.accessType).map(AccessType::valueOf),
                Optional.ofNullable(e.directed), toProperties(e.properties), SortOrder.of(e.sortOrder), e.supported,
                raw(e.rawXml));
    }

    static void apply(Relationship d, UUID modelId, RelationshipEntity e) {
        e.id = d.id().value();
        e.modelId = modelId;
        e.folderId = d.folderId().value();
        e.archiId = d.archiId().value();
        e.archiType = d.archiType().value();
        e.sourceElementId = d.source() instanceof ElementId id ? id.value() : null;
        e.sourceRelationshipId = d.source() instanceof RelationshipId id ? id.value() : null;
        e.targetElementId = d.target() instanceof ElementId id ? id.value() : null;
        e.targetRelationshipId = d.target() instanceof RelationshipId id ? id.value() : null;
        e.name = d.name().orElse(null);
        e.documentation = d.documentation().orElse(null);
        e.accessType = d.accessType().map(Enum::name).orElse(null);
        e.directed = d.directed().orElse(null);
        e.sortOrder = d.sortOrder().value();
        e.supported = d.supported();
        e.rawXml = d.rawXml().map(RawXml::value).orElse(null);
        replaceProperties(e.properties, d.properties());
    }

    private static ConceptRef concept(UUID element, UUID relationship) {
        return element != null ? ElementId.of(element) : RelationshipId.of(relationship);
    }

    // ── Представления ───────────────────────────────────────────────

    static ViewHeader toViewHeader(ViewEntity e) {
        return new ViewHeader(ViewId.of(e.id), ModelId.of(e.modelId), FolderId.of(e.folderId), ArchiId.of(e.archiId),
                DiagramType.of(e.archiType), e.name, Optional.ofNullable(e.documentation),
                Optional.ofNullable(e.viewpoint), toProperties(e.properties), SortOrder.of(e.sortOrder),
                raw(e.rawXml), e.version);
    }

    static ViewRef toViewRef(ViewEntity e) {
        return new ViewRef(ViewId.of(e.id), FolderId.of(e.folderId), ArchiId.of(e.archiId),
                DiagramType.of(e.archiType), e.name, SortOrder.of(e.sortOrder));
    }

    static void apply(ViewHeader h, ViewEntity e) {
        e.id = h.id().value();
        e.modelId = h.modelId().value();
        e.folderId = h.folderId().value();
        e.archiId = h.archiId().value();
        e.archiType = h.archiType().value();
        e.name = h.name();
        e.documentation = h.documentation().orElse(null);
        e.viewpoint = h.viewpoint().orElse(null);
        e.sortOrder = h.sortOrder().value();
        e.rawXml = h.rawXml().map(RawXml::value).orElse(null);
        replaceProperties(e.properties, h.properties());
    }

    static ViewNode toNode(ViewNodeEntity e) {
        return new ViewNode(ViewNodeId.of(e.id), Optional.ofNullable(e.parentId).map(ViewNodeId::of),
                ArchiId.of(e.archiId), DiagramType.of(e.archiType), Optional.ofNullable(e.elementId).map(ElementId::of),
                new Bounds(e.x, e.y, e.width, e.height),
                style(e.fillColor, e.font, e.fontColor, e.lineColor, e.textAlignment),
                Optional.ofNullable(e.label), Optional.ofNullable(e.content), SortOrder.of(e.sortOrder),
                raw(e.rawXml));
    }

    static void apply(ViewNode n, UUID modelId, UUID viewId, ViewNodeEntity e) {
        e.id = n.id().value();
        e.modelId = modelId;
        e.viewId = viewId;
        e.parentId = n.parentId().map(ViewNodeId::value).orElse(null);
        e.archiId = n.archiId().value();
        e.archiType = n.archiType().value();
        e.kind = n.kind().name();
        e.elementId = n.elementId().map(ElementId::value).orElse(null);
        e.x = n.bounds().x();
        e.y = n.bounds().y();
        e.width = n.bounds().width();
        e.height = n.bounds().height();
        e.fillColor = n.style().fillColor().orElse(null);
        e.font = n.style().font().orElse(null);
        e.fontColor = n.style().fontColor().orElse(null);
        e.lineColor = n.style().lineColor().orElse(null);
        e.textAlignment = n.style().textAlignment().orElse(null);
        e.label = n.label().orElse(null);
        e.content = n.content().orElse(null);
        e.sortOrder = n.sortOrder().value();
        e.rawXml = n.rawXml().map(RawXml::value).orElse(null);
    }

    static ViewEdge toEdge(ViewEdgeEntity e) {
        return new ViewEdge(ViewEdgeId.of(e.id), ArchiId.of(e.archiId), DiagramType.of(e.archiType),
                Optional.ofNullable(e.relationshipId).map(RelationshipId::of), endpoint(e.sourceNodeId, e.sourceEdgeId),
                endpoint(e.targetNodeId, e.targetEdgeId), Bendpoints.parse(e.bendpoints),
                style(e.fillColor, e.font, e.fontColor, e.lineColor, e.textAlignment), SortOrder.of(e.sortOrder),
                raw(e.rawXml));
    }

    static void apply(ViewEdge d, UUID modelId, UUID viewId, ViewEdgeEntity e) {
        e.id = d.id().value();
        e.modelId = modelId;
        e.viewId = viewId;
        e.archiId = d.archiId().value();
        e.archiType = d.archiType().value();
        e.relationshipId = d.relationshipId().map(RelationshipId::value).orElse(null);
        e.sourceNodeId = d.source() instanceof ViewNodeId id ? id.value() : null;
        e.sourceEdgeId = d.source() instanceof ViewEdgeId id ? id.value() : null;
        e.targetNodeId = d.target() instanceof ViewNodeId id ? id.value() : null;
        e.targetEdgeId = d.target() instanceof ViewEdgeId id ? id.value() : null;
        e.bendpoints = Bendpoints.format(d.bendpoints());
        e.fillColor = d.style().fillColor().orElse(null);
        e.font = d.style().font().orElse(null);
        e.fontColor = d.style().fontColor().orElse(null);
        e.lineColor = d.style().lineColor().orElse(null);
        e.textAlignment = d.style().textAlignment().orElse(null);
        e.sortOrder = d.sortOrder().value();
        e.rawXml = d.rawXml().map(RawXml::value).orElse(null);
    }

    private static ViewEndpoint endpoint(UUID node, UUID edge) {
        return node != null ? ViewNodeId.of(node) : ViewEdgeId.of(edge);
    }

    private static StyleOverride style(String fill, String font, String fontColor, String line, Integer alignment) {
        return new StyleOverride(Optional.ofNullable(fill), Optional.ofNullable(font), Optional.ofNullable(fontColor),
                Optional.ofNullable(line), Optional.ofNullable(alignment));
    }

    // ── Общее ───────────────────────────────────────────────────────

    static List<PropertyEntry> toProperties(List<PropertyEmbeddable> rows) {
        return rows.stream().map(p -> new PropertyEntry(p.key(), p.value(), SortOrder.of(p.sortOrder()))).toList();
    }

    /** Коллекцию трогаем, только если она изменилась: замена экземпляра — переписывание строк. */
    private static void replaceProperties(List<PropertyEmbeddable> target, List<PropertyEntry> source) {
        List<PropertyEmbeddable> wanted = new ArrayList<>();
        source.forEach(p -> wanted.add(new PropertyEmbeddable(p.sortOrder().value(), p.key(), p.value())));
        if (!target.equals(wanted)) {
            target.clear();
            target.addAll(wanted);
        }
    }

    private static Optional<RawXml> raw(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isEmpty()).map(RawXml::new);
    }

    /** Точки перегиба в {@code jsonb}: массив четвёрок, без библиотеки — формат закрыт и мал. */
    static final class Bendpoints {

        private Bendpoints() {
        }

        static String format(List<Bendpoint> points) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < points.size(); i++) {
                Bendpoint p = points.get(i);
                json.append(i == 0 ? "" : ",").append('[').append(p.startX()).append(',').append(p.startY())
                        .append(',').append(p.endX()).append(',').append(p.endY()).append(']');
            }
            return json.append(']').toString();
        }

        static List<Bendpoint> parse(String json) {
            String digits = json.replaceAll("[\\s\\[\\]]", " ").trim();
            if (digits.replace(",", "").isBlank()) {
                return List.of();
            }
            String[] numbers = json.replaceAll("[\\[\\]\\s]", "").split(",");
            List<Bendpoint> points = new ArrayList<>();
            for (int i = 0; i + 3 < numbers.length; i += 4) {
                points.add(new Bendpoint(Integer.parseInt(numbers[i]), Integer.parseInt(numbers[i + 1]),
                        Integer.parseInt(numbers[i + 2]), Integer.parseInt(numbers[i + 3])));
            }
            return points;
        }
    }
}
