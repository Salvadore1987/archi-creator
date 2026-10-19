package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.BendpointDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.EdgeBendpoints;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ElementDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.Finding;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.FolderDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.LayoutPatch;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.LockInfo;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ModelSummary;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ModelTree;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.NodeBounds;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.PlacementDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.PropertyDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.RelationTypeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.RelationshipDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.StyleDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.VersionInfo;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewEdgeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewNodeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewPayload;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewSummary;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LockResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PropertyResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ValidationFindingResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.service.RelationshipService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.StyleOverride;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutProposal;

/** Агрегаты — в DTO. Списки — в порядке {@code sort_order}: клиенту не нужно сортировать заново. */
public final class DtoMapper {

    private DtoMapper() {
    }

    /** Заголовок модели для списка и ответов на правку. */
    public static ModelSummary summary(ModelSummaryResponse h) {
        return new ModelSummary(h.id(), h.workspaceId(), h.archiId(), h.name(), h.documentation(), h.status(),
                h.archiVersion(), h.createdBy(), h.createdAt(), h.updatedAt());
    }

    /** Открытая модель целиком: заголовок, папки, элементы, связи, представления и их размещения. */
    public static ModelTree tree(ModelTreeResponse tree) {
        return new ModelTree(summary(tree.model()),
                tree.folders().stream().map(f -> new FolderDto(f.id(), f.parentId(), f.archiId(), f.name(),
                        f.folderType(), f.sortOrder())).toList(),
                tree.elements().stream().map(e -> new ElementDto(e.id(), e.folderId(), e.archiId(), e.archiType(),
                        e.layer(), e.name(), e.documentation(), propertyDtos(e.properties()), e.supported(),
                        e.sortOrder())).toList(),
                tree.relationships().stream().map(r -> new RelationshipDto(r.id(), r.folderId(), r.archiId(),
                        r.archiType(), r.sourceId(), r.targetId(), r.name(), r.documentation(), r.accessType(),
                        r.directed(), propertyDtos(r.properties()), r.supported(), r.sortOrder(), null)).toList(),
                tree.views().stream().map(v -> new ViewSummary(v.id(), v.folderId(), v.archiId(), v.archiType(),
                        v.name(), v.sortOrder())).toList(),
                tree.placements().stream().map(p -> new PlacementDto(p.viewId(), p.elementIds(),
                        p.relationshipIds())).toList());
    }

    private static List<PropertyDto> propertyDtos(List<PropertyResponse> properties) {
        return properties.stream().map(p -> new PropertyDto(p.key(), p.value())).toList();
    }

    /** Папка дерева; у корневой — тип, у вложенной — родитель. */
    public static FolderDto folder(ModelFolder f) {
        return new FolderDto(f.id().value(), f.parentId() == null ? null : f.parentId().value(), f.archiId().value(),
                f.name(), f.folderType() == null ? null : f.folderType().name(), f.sortOrder().value());
    }

    /** Элемент со свойствами в порядке документа. */
    public static ElementDto element(Element e) {
        return new ElementDto(e.id().value(), e.folderId().value(), e.archiId().value(), e.archiType().value(),
                e.layer().name(), e.name(), e.documentation(), properties(e.properties()), e.supported(),
                e.sortOrder().value());
    }

    /** Связь; {@code edgeId} — ребро, нарисованное вместе с ней, иначе {@code null}. */
    public static RelationshipDto relationship(Relationship r, UUID edgeId) {
        return new RelationshipDto(r.id().value(), r.folderId().value(), r.archiId().value(), r.archiType().value(),
                r.source().value(), r.target().value(), r.name(), r.documentation(),
                r.accessType() == null ? null : r.accessType().name(), r.directed(), properties(r.properties()),
                r.supported(), r.sortOrder().value(), edgeId);
    }

    /** Заголовок загруженного представления — без узлов и рёбер. */
    public static ViewSummary view(View v) {
        ViewHeader h = v.header();
        return new ViewSummary(h.id().value(), h.folderId().value(), h.archiId().value(), h.archiType().value(),
                h.name(), h.sortOrder().value());
    }

    /** Представление целиком: заголовок, узлы и рёбра с геометрией и стилем. */
    public static ViewPayload payload(View view) {
        ViewHeader h = view.header();
        return new ViewPayload(h.id().value(), h.modelId().value(), h.folderId().value(), h.archiId().value(),
                h.archiType().value(), h.name(), h.documentation(), h.viewpoint(),
                properties(h.properties()), h.editable(),
                view.nodes().values().stream().sorted(Comparator.comparing(ViewNode::sortOrder)).map(DtoMapper::node)
                        .toList(),
                view.edges().values().stream().sorted(Comparator.comparing(ViewEdge::sortOrder)).map(DtoMapper::edge)
                        .toList());
    }

    /** Узел представления; границы — относительно родителя. */
    public static ViewNodeDto node(ViewNode n) {
        return new ViewNodeDto(n.id().value(), n.parentId() == null ? null : n.parentId().value(), n.archiId().value(),
                n.archiType().value(), n.kind().name(), n.elementId() == null ? null : n.elementId().value(),
                n.bounds().x(), n.bounds().y(), n.bounds().width(), n.bounds().height(), style(n.style()),
                n.label(), n.content(), n.sortOrder().value());
    }

    /** Ребро представления с точками перегиба и стилем. */
    public static ViewEdgeDto edge(ViewEdge e) {
        return new ViewEdgeDto(e.id().value(), e.archiId().value(), e.archiType().value(),
                e.relationshipId() == null ? null : e.relationshipId().value(), e.source().value(), e.target().value(),
                e.bendpoints().stream().map(b -> new BendpointDto(b.startX(), b.startY(), b.endX(), b.endY())).toList(),
                style(e.style()), e.sortOrder().value());
    }

    /** Версия модели без снимка; есть ли снимок — отдельным флагом. */
    public static VersionInfo version(ModelVersion v) {
        return new VersionInfo(v.versionNo(), v.author(), v.comment(), v.label(),
                v.createdAt(), v.hasSnapshot(), v.gitSha());
    }

    /** Блокировка модели: владелец и срок. */
    public static LockInfo lock(LockResponse l) {
        return new LockInfo(l.modelId(), l.owner(), l.acquiredAt(), l.expiresAt());
    }

    /** Находка проверки модели: текст уже на языке запроса. */
    public static Finding finding(ValidationFindingResponse f) {
        return new Finding(f.severity(), f.code(), f.message(), f.targetKind(), f.targetId(), f.suggestion());
    }

    private static StyleDto style(StyleOverride s) {
        return s.empty() ? null : new StyleDto(s.fillColor(), s.font(),
                s.fontColor(), s.lineColor(), s.textAlignment());
    }

    private static List<PropertyDto> properties(List<PropertyEntry> entries) {
        return entries.stream().sorted(Comparator.comparing(PropertyEntry::sortOrder))
                .map(p -> new PropertyDto(p.key(), p.value())).toList();
    }

    static RelationTypeDto relationType(RelationshipService.Suggestion suggestion) {
        return new RelationTypeDto(suggestion.type().archiType().value(), suggestion.type().name(),
                suggestion.byDefault(), suggestion.nesting());
    }

    /** Предложение авторазметки в форме правки геометрии: точки перегиба рёбер — пустые. */
    static LayoutPatch layoutPatch(LayoutProposal proposal) {
        return new LayoutPatch(proposal.nodes().entrySet().stream()
                .map(e -> new NodeBounds(e.getKey().value(), e.getValue().x(), e.getValue().y(), e.getValue().width(),
                        e.getValue().height()))
                .toList(),
                proposal.clearedEdges().stream().map(id -> new EdgeBendpoints(id.value(), List.of())).toList());
    }
}
