package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import java.util.Comparator;
import java.util.List;
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
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.RelationshipService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.validation.ValidationFinding;
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
    public static ModelSummary summary(ModelHeader h) {
        return new ModelSummary(h.id().value(), h.workspaceId().value(), h.archiId().value(), h.name(),
                h.documentation().orElse(null), h.status().name(), h.archiVersion(), h.createdBy(), h.createdAt(),
                h.updatedAt());
    }

    /** Открытая модель целиком: заголовок, папки, элементы, связи, представления и их размещения. */
    public static ModelTree tree(ModelQueryService.OpenedModel opened) {
        ArchitectureModel model = opened.model();
        return new ModelTree(summary(model.header()),
                model.folders().values().stream().sorted(Comparator.comparing(ModelFolder::sortOrder))
                        .map(DtoMapper::folder).toList(),
                model.elements().values().stream().sorted(Comparator.comparing(Element::sortOrder))
                        .map(DtoMapper::element).toList(),
                model.relationships().values().stream().sorted(Comparator.comparing(Relationship::sortOrder))
                        .map(r -> relationship(r, null)).toList(),
                model.views().stream().sorted(Comparator.comparing(ViewRef::sortOrder)).map(DtoMapper::view).toList(),
                opened.placements().stream().map(p -> new PlacementDto(p.viewId().value(),
                        p.elementIds().stream().map(e -> e.value()).toList(),
                        p.relationshipIds().stream().map(r -> r.value()).toList())).toList());
    }

    /** Папка дерева; у корневой — тип, у вложенной — родитель. */
    public static FolderDto folder(ModelFolder f) {
        return new FolderDto(f.id().value(), f.parentId().map(FolderId::value).orElse(null), f.archiId().value(),
                f.name(), f.folderType().map(Enum::name).orElse(null), f.sortOrder().value());
    }

    /** Элемент со свойствами в порядке документа. */
    public static ElementDto element(Element e) {
        return new ElementDto(e.id().value(), e.folderId().value(), e.archiId().value(), e.archiType().value(),
                e.layer().name(), e.name(), e.documentation().orElse(null), properties(e.properties()), e.supported(),
                e.sortOrder().value());
    }

    /** Связь; {@code edgeId} — ребро, нарисованное вместе с ней, иначе {@code null}. */
    public static RelationshipDto relationship(Relationship r, java.util.UUID edgeId) {
        return new RelationshipDto(r.id().value(), r.folderId().value(), r.archiId().value(), r.archiType().value(),
                r.source().value(), r.target().value(), r.name().orElse(null), r.documentation().orElse(null),
                r.accessType().map(Enum::name).orElse(null), r.directed().orElse(null), properties(r.properties()),
                r.supported(), r.sortOrder().value(), edgeId);
    }

    /** Представление из дерева модели — без узлов и рёбер. */
    public static ViewSummary view(ViewRef v) {
        return new ViewSummary(v.id().value(), v.folderId().value(), v.archiId().value(), v.archiType().value(),
                v.name(), v.sortOrder().value());
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
                h.archiType().value(), h.name(), h.documentation().orElse(null), h.viewpoint().orElse(null),
                properties(h.properties()), h.editable(),
                view.nodes().values().stream().sorted(Comparator.comparing(ViewNode::sortOrder)).map(DtoMapper::node)
                        .toList(),
                view.edges().values().stream().sorted(Comparator.comparing(ViewEdge::sortOrder)).map(DtoMapper::edge)
                        .toList());
    }

    /** Узел представления; границы — относительно родителя. */
    public static ViewNodeDto node(ViewNode n) {
        return new ViewNodeDto(n.id().value(), n.parentId().map(p -> p.value()).orElse(null), n.archiId().value(),
                n.archiType().value(), n.kind().name(), n.elementId().map(e -> e.value()).orElse(null),
                n.bounds().x(), n.bounds().y(), n.bounds().width(), n.bounds().height(), style(n.style()),
                n.label().orElse(null), n.content().orElse(null), n.sortOrder().value());
    }

    /** Ребро представления с точками перегиба и стилем. */
    public static ViewEdgeDto edge(ViewEdge e) {
        return new ViewEdgeDto(e.id().value(), e.archiId().value(), e.archiType().value(),
                e.relationshipId().map(r -> r.value()).orElse(null), e.source().value(), e.target().value(),
                e.bendpoints().stream().map(b -> new BendpointDto(b.startX(), b.startY(), b.endX(), b.endY())).toList(),
                style(e.style()), e.sortOrder().value());
    }

    /** Версия модели без снимка; есть ли снимок — отдельным флагом. */
    public static VersionInfo version(ModelVersion v) {
        return new VersionInfo(v.versionNo(), v.author(), v.comment().orElse(null), v.label().orElse(null),
                v.createdAt(), v.snapshot().isPresent(), v.gitSha().orElse(null));
    }

    /** Блокировка модели: владелец и срок. */
    public static LockInfo lock(ModelLock l) {
        return new LockInfo(l.modelId().value(), l.owner(), l.acquiredAt(), l.expiresAt());
    }

    /** Находка с текстом на языке запроса: сообщение и подсказка хранятся ключами. */
    public static Finding finding(ValidationFinding f, TextCatalog text) {
        return new Finding(f.severity().name(), f.code(), text.text(f.message()), f.targetKind(), f.targetId(),
                f.suggestion().map(text::text).orElse(null));
    }

    private static StyleDto style(StyleOverride s) {
        return s.empty() ? null : new StyleDto(s.fillColor().orElse(null), s.font().orElse(null),
                s.fontColor().orElse(null), s.lineColor().orElse(null), s.textAlignment().orElse(null));
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
                .map(e -> new NodeBounds(e.getKey(), e.getValue().x(), e.getValue().y(), e.getValue().width(),
                        e.getValue().height()))
                .toList(),
                proposal.clearedEdges().stream().map(id -> new EdgeBendpoints(id, List.of())).toList());
    }
}
