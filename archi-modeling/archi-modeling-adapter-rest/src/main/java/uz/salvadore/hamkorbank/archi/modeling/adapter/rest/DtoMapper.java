package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.AclEntryDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.AutoLayoutRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.BendpointDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.EdgeBendpoints;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.EdgePlacementRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ElementDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ElementTypeDto;
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
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.SaveResult;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.StyleDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.VersionInfo;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewEdgeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewNodeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewPayload;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewSummary;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.WorkspaceDto;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.PropertyValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceAccessListCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveViewLayoutCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ProposeViewLayoutQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.AccessListResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.FolderResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LayoutProposalResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LockResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PropertyResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.SaveVersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ValidationFindingResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.WorkspaceResponse;

/**
 * Перевод на границе HTTP: тело запроса — в команду или запрос сценария, результат — в DTO.
 * Списки — в порядке {@code sort_order}: клиенту не нужно сортировать заново.
 */
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
                tree.folders().stream().map(DtoMapper::folder).toList(),
                tree.elements().stream().map(DtoMapper::element).toList(),
                tree.relationships().stream().map(r -> relationship(r, null)).toList(),
                tree.views().stream().map(v -> new ViewSummary(v.id(), v.folderId(), v.archiId(), v.archiType(),
                        v.name(), v.sortOrder())).toList(),
                tree.placements().stream().map(p -> new PlacementDto(p.viewId(), p.elementIds(),
                        p.relationshipIds())).toList());
    }

    private static List<PropertyDto> propertyDtos(List<PropertyResponse> properties) {
        return properties.stream().map(p -> new PropertyDto(p.key(), p.value())).toList();
    }

    /** Папка дерева; у корневой — тип, у вложенной — родитель. */
    public static FolderDto folder(FolderResponse f) {
        return new FolderDto(f.id(), f.parentId(), f.archiId(), f.name(), f.folderType(), f.sortOrder());
    }

    /** Элемент со свойствами в порядке документа. */
    public static ElementDto element(ElementResponse e) {
        return new ElementDto(e.id(), e.folderId(), e.archiId(), e.archiType(), e.layer(), e.name(),
                e.documentation(), propertyDtos(e.properties()), e.supported(), e.sortOrder());
    }

    /** Связь; {@code edgeId} — ребро, нарисованное вместе с ней, иначе {@code null}. */
    public static RelationshipDto relationship(RelationshipResponse r, @Nullable UUID edgeId) {
        return new RelationshipDto(r.id(), r.folderId(), r.archiId(), r.archiType(), r.sourceId(), r.targetId(),
                r.name(), r.documentation(), r.accessType(), r.directed(), propertyDtos(r.properties()),
                r.supported(), r.sortOrder(), edgeId);
    }

    /** Заголовок представления — без узлов и рёбер. */
    public static ViewSummary view(ViewSummaryResponse v) {
        return new ViewSummary(v.id(), v.folderId(), v.archiId(), v.archiType(), v.name(), v.sortOrder());
    }

    /** Представление целиком: заголовок, узлы и рёбра с геометрией и стилем. */
    public static ViewPayload payload(ViewResponse v) {
        return new ViewPayload(v.id(), v.modelId(), v.folderId(), v.archiId(), v.archiType(), v.name(),
                v.documentation(), v.viewpoint(), propertyDtos(v.properties()), v.editable(),
                v.nodes().stream().map(DtoMapper::node).toList(), v.edges().stream().map(DtoMapper::edge).toList());
    }

    /** Узел представления; границы — относительно родителя. */
    public static ViewNodeDto node(ViewNodeResponse n) {
        return new ViewNodeDto(n.id(), n.parentId(), n.archiId(), n.archiType(), n.kind(), n.elementId(), n.x(),
                n.y(), n.width(), n.height(), style(n.style()), n.label(), n.content(), n.sortOrder());
    }

    /** Ребро представления с точками перегиба и стилем. */
    public static ViewEdgeDto edge(ViewEdgeResponse e) {
        return new ViewEdgeDto(e.id(), e.archiId(), e.archiType(), e.relationshipId(), e.sourceId(), e.targetId(),
                e.bendpoints().stream().map(b -> new BendpointDto(b.startX(), b.startY(), b.endX(), b.endY()))
                        .toList(),
                style(e.style()), e.sortOrder());
    }

    /** Ребро новой связи; без {@code view} связь рисуется без ребра. */
    public static CreateRelationshipCommand.@Nullable Edge edge(@Nullable EdgePlacementRequest view) {
        return view == null ? null : new CreateRelationshipCommand.Edge(view.viewId(), view.sourceNodeId(),
                view.targetNodeId(), view.edgeId(), view.edgeArchiId());
    }

    /** Версия модели без снимка; есть ли снимок — отдельным флагом. */
    public static VersionInfo version(VersionResponse v) {
        return new VersionInfo(v.versionNo(), v.author(), v.comment(), v.label(), v.createdAt(),
                v.snapshotAvailable(), v.gitSha());
    }

    /** Блокировка модели: владелец и срок. */
    public static LockInfo lock(LockResponse l) {
        return new LockInfo(l.modelId(), l.owner(), l.acquiredAt(), l.expiresAt());
    }

    /** Находка проверки модели: текст уже на языке запроса. */
    public static Finding finding(ValidationFindingResponse f) {
        return new Finding(f.severity(), f.code(), f.message(), f.targetKind(), f.targetId(), f.suggestion());
    }

    private static @Nullable StyleDto style(@Nullable StyleValue s) {
        return s == null ? null
                : new StyleDto(s.fillColor(), s.font(), s.fontColor(), s.lineColor(), s.textAlignment());
    }

    /** Собственный стиль правки; {@code null} — снять. */
    public static @Nullable StyleValue style(@Nullable StyleDto dto) {
        return dto == null ? null
                : new StyleValue(dto.fillColor(), dto.font(), dto.fontColor(), dto.lineColor(), dto.textAlignment());
    }

    static RelationTypeDto relationType(RelationTypeResponse r) {
        return new RelationTypeDto(r.archiType(), r.type(), r.byDefault(), r.nesting());
    }

    static ElementTypeDto elementType(ElementTypeResponse t) {
        return new ElementTypeDto(t.archiType(), t.kind(), t.layer(), t.phase(), t.supported());
    }

    /** Предложение авторазметки в форме правки геометрии: точки перегиба рёбер — пустые. */
    static LayoutPatch layoutPatch(LayoutProposalResponse proposal) {
        return new LayoutPatch(proposal.nodes().stream()
                .map(n -> new NodeBounds(n.id(), n.x(), n.y(), n.width(), n.height())).toList(),
                proposal.clearedEdges().stream().map(id -> new EdgeBendpoints(id, List.of())).toList());
    }

    // ── Запросы ─────────────────────────────────────────────────────

    /** Рабочее пространство: идентификатор и имя. */
    public static WorkspaceDto workspace(WorkspaceResponse w) {
        return new WorkspaceDto(w.id(), w.name());
    }

    /** Список доступа в ответе: записи в порядке сценария. */
    public static List<AclEntryDto> accessList(AccessListResponse list) {
        return list.entries().stream().map(e -> new AclEntryDto(e.principalType(), e.principal(), e.access()))
                .toList();
    }

    /** Новый список доступа целиком — в команду замены. */
    public static ReplaceAccessListCommand replaceAccessList(UUID modelId, List<AclEntryDto> entries) {
        return new ReplaceAccessListCommand(modelId, entries.stream()
                .map(e -> new ReplaceAccessListCommand.Entry(e.principalType(), e.principal(), e.access()))
                .toList());
    }

    /** Итог сохранения: версия и признак, что она новая. */
    public static SaveResult saveResult(SaveVersionResponse result) {
        return new SaveResult(version(result.version()), result.created());
    }

    /**
     * Свойства правки; {@code null} — не менять. Значение без {@code value} — пустая строка:
     * так свойство без значения читает и файл.
     */
    public static @Nullable List<PropertyValue> properties(@Nullable List<PropertyDto> properties) {
        return properties == null ? null : properties.stream()
                .map(p -> new PropertyValue(p.key(), p.value() == null ? "" : p.value())).toList();
    }

    /** Точки перегиба; не переданы — ребро прямое. */
    public static List<BendpointValue> bendpoints(@Nullable List<BendpointDto> points) {
        return Optional.ofNullable(points).orElse(List.of()).stream()
                .map(b -> new BendpointValue(b.startX(), b.startY(), b.endX(), b.endY())).toList();
    }

    /** Геометрия после перемещений; не переданный список — без изменений. */
    public static SaveViewLayoutCommand layout(UUID viewId, LayoutPatch patch) {
        return new SaveViewLayoutCommand(viewId,
                Optional.ofNullable(patch.nodes()).orElse(List.of()).stream()
                        .map(n -> new SaveViewLayoutCommand.NodeBounds(n.id(), n.x(), n.y(), n.width(), n.height()))
                        .toList(),
                Optional.ofNullable(patch.edges()).orElse(List.of()).stream()
                        .map(e -> new SaveViewLayoutCommand.EdgeBendpoints(e.id(), bendpoints(e.bendpoints())))
                        .toList());
    }

    /** Граф представления из браузера — в запрос авторазметки. */
    public static ProposeViewLayoutQuery layoutQuery(UUID viewId, AutoLayoutRequest request) {
        return new ProposeViewLayoutQuery(viewId, request.strategy(),
                request.nodes().stream().map(n -> new ProposeViewLayoutQuery.Node(n.id(), n.parentId(),
                        n.conceptType(), n.x(), n.y(), n.width(), n.height())).toList(),
                Optional.ofNullable(request.edges()).orElse(List.of()).stream()
                        .map(e -> new ProposeViewLayoutQuery.Edge(e.id(), e.sourceId(), e.targetId())).toList());
    }
}
