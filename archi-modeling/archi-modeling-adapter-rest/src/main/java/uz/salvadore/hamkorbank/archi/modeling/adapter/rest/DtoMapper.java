package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import java.util.Comparator;
import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.BendpointDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ElementDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.Finding;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.FolderDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.LockInfo;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ModelSummary;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ModelTree;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.PropertyDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.RelationshipDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.StyleDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.VersionInfo;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewEdgeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewNodeDto;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewPayload;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.ViewSummary;
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

/** Агрегаты — в DTO. Списки — в порядке {@code sort_order}: клиенту не нужно сортировать заново. */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static ModelSummary summary(ModelHeader h) {
        return new ModelSummary(h.id().value(), h.workspaceId().value(), h.archiId().value(), h.name(),
                h.documentation().orElse(null), h.status().name(), h.archiVersion(), h.createdBy(), h.createdAt(),
                h.updatedAt());
    }

    public static ModelTree tree(ArchitectureModel model) {
        return new ModelTree(summary(model.header()),
                model.folders().values().stream().sorted(Comparator.comparing(ModelFolder::sortOrder))
                        .map(DtoMapper::folder).toList(),
                model.elements().values().stream().sorted(Comparator.comparing(Element::sortOrder))
                        .map(DtoMapper::element).toList(),
                model.relationships().values().stream().sorted(Comparator.comparing(Relationship::sortOrder))
                        .map(r -> relationship(r, null)).toList(),
                model.views().stream().sorted(Comparator.comparing(ViewRef::sortOrder)).map(DtoMapper::view).toList());
    }

    public static FolderDto folder(ModelFolder f) {
        return new FolderDto(f.id().value(), f.parentId().map(FolderId::value).orElse(null), f.archiId().value(),
                f.name(), f.folderType().map(Enum::name).orElse(null), f.sortOrder().value());
    }

    public static ElementDto element(Element e) {
        return new ElementDto(e.id().value(), e.folderId().value(), e.archiId().value(), e.archiType().value(),
                e.layer().name(), e.name(), e.documentation().orElse(null), properties(e.properties()), e.supported(),
                e.sortOrder().value());
    }

    public static RelationshipDto relationship(Relationship r, java.util.UUID edgeId) {
        return new RelationshipDto(r.id().value(), r.folderId().value(), r.archiId().value(), r.archiType().value(),
                r.source().value(), r.target().value(), r.name().orElse(null), r.documentation().orElse(null),
                r.accessType().map(Enum::name).orElse(null), r.directed().orElse(null), properties(r.properties()),
                r.supported(), r.sortOrder().value(), edgeId);
    }

    public static ViewSummary view(ViewRef v) {
        return new ViewSummary(v.id().value(), v.folderId().value(), v.archiId().value(), v.archiType().value(),
                v.name(), v.sortOrder().value());
    }

    public static ViewSummary view(View v) {
        ViewHeader h = v.header();
        return new ViewSummary(h.id().value(), h.folderId().value(), h.archiId().value(), h.archiType().value(),
                h.name(), h.sortOrder().value());
    }

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

    public static ViewNodeDto node(ViewNode n) {
        return new ViewNodeDto(n.id().value(), n.parentId().map(p -> p.value()).orElse(null), n.archiId().value(),
                n.archiType().value(), n.kind().name(), n.elementId().map(e -> e.value()).orElse(null),
                n.bounds().x(), n.bounds().y(), n.bounds().width(), n.bounds().height(), style(n.style()),
                n.label().orElse(null), n.content().orElse(null), n.sortOrder().value());
    }

    public static ViewEdgeDto edge(ViewEdge e) {
        return new ViewEdgeDto(e.id().value(), e.archiId().value(), e.archiType().value(),
                e.relationshipId().map(r -> r.value()).orElse(null), e.source().value(), e.target().value(),
                e.bendpoints().stream().map(b -> new BendpointDto(b.startX(), b.startY(), b.endX(), b.endY())).toList(),
                style(e.style()), e.sortOrder().value());
    }

    public static VersionInfo version(ModelVersion v) {
        return new VersionInfo(v.versionNo(), v.author(), v.comment().orElse(null), v.label().orElse(null),
                v.createdAt(), v.snapshot().isPresent(), v.gitSha().orElse(null));
    }

    public static LockInfo lock(ModelLock l) {
        return new LockInfo(l.modelId().value(), l.owner(), l.acquiredAt(), l.expiresAt());
    }

    public static Finding finding(ValidationFinding f) {
        return new Finding(f.severity().name(), f.code(), f.message(), f.targetKind(), f.targetId(),
                f.suggestion().orElse(null));
    }

    private static StyleDto style(StyleOverride s) {
        return s.empty() ? null : new StyleDto(s.fillColor().orElse(null), s.font().orElse(null),
                s.fontColor().orElse(null), s.lineColor().orElse(null), s.textAlignment().orElse(null));
    }

    private static List<PropertyDto> properties(List<PropertyEntry> entries) {
        return entries.stream().sorted(Comparator.comparing(PropertyEntry::sortOrder))
                .map(p -> new PropertyDto(p.key(), p.value())).toList();
    }
}
