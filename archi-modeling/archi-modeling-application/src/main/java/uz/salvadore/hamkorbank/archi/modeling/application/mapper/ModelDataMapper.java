package uz.salvadore.hamkorbank.archi.modeling.application.mapper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.FolderResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlacementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PropertyResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ValidationFindingResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewPlacements;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.validation.ValidationFinding;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/** Модель и её содержимое — в результаты сценариев. */
public final class ModelDataMapper {

    private ModelDataMapper() {
    }

    /** Заголовок модели. */
    public static ModelSummaryResponse summary(ModelHeader h) {
        return new ModelSummaryResponse(h.id().value(), h.workspaceId().value(), h.archiId().value(), h.name(),
                h.documentation(), h.status().name(), h.archiVersion(), h.createdBy(), h.createdAt(),
                h.updatedAt());
    }

    /**
     * Дерево модели в порядке дерева и размещения — по записи на каждое представление,
     * пустое тоже: маркеры «не размещён» и «×N» дереву нужны сразу.
     */
    public static ModelTreeResponse tree(ArchitectureModel model, List<ViewPlacements> found) {
        Map<ViewId, ViewPlacements> byView = new HashMap<>();
        found.forEach(p -> byView.put(p.viewId(), p));
        List<ViewRef> views = model.views().stream().sorted(Comparator.comparing(ViewRef::sortOrder)).toList();
        return new ModelTreeResponse(summary(model.header()),
                model.folders().values().stream().sorted(Comparator.comparing(ModelFolder::sortOrder))
                        .map(ModelDataMapper::folder).toList(),
                model.elements().values().stream().sorted(Comparator.comparing(Element::sortOrder))
                        .map(ModelDataMapper::element).toList(),
                model.relationships().values().stream().sorted(Comparator.comparing(Relationship::sortOrder))
                        .map(ModelDataMapper::relationship).toList(),
                views.stream().map(ModelDataMapper::view).toList(),
                views.stream().map(v -> byView.getOrDefault(v.id(), ViewPlacements.empty(v.id())))
                        .map(ModelDataMapper::placement).toList());
    }

    /** Папка дерева. */
    public static FolderResponse folder(ModelFolder f) {
        return new FolderResponse(f.id().value(), f.parentId() == null ? null : f.parentId().value(),
                f.archiId().value(), f.name(), f.folderType() == null ? null : f.folderType().name(),
                f.sortOrder().value());
    }

    /** Элемент со свойствами в порядке документа. */
    public static ElementResponse element(Element e) {
        return new ElementResponse(e.id().value(), e.folderId().value(), e.archiId().value(), e.archiType().value(),
                e.layer().name(), e.name(), e.documentation(), properties(e.properties()), e.supported(),
                e.sortOrder().value());
    }

    /** Связь со свойствами в порядке документа. */
    public static RelationshipResponse relationship(Relationship r) {
        return new RelationshipResponse(r.id().value(), r.folderId().value(), r.archiId().value(),
                r.archiType().value(), r.source().value(), r.target().value(), r.name(), r.documentation(),
                r.accessType() == null ? null : r.accessType().name(), r.directed(), properties(r.properties()),
                r.supported(), r.sortOrder().value());
    }

    /** Представление из дерева модели — без узлов и рёбер. */
    public static ViewSummaryResponse view(ViewRef v) {
        return new ViewSummaryResponse(v.id().value(), v.folderId().value(), v.archiId().value(),
                v.archiType().value(), v.name(), v.sortOrder().value());
    }

    /** Находка проверки с текстом на языке запроса: сообщение и подсказка хранятся ключами. */
    public static ValidationFindingResponse finding(ValidationFinding f, TextCatalog text) {
        return new ValidationFindingResponse(f.severity().name(), f.code(), text.text(f.message()), f.targetKind(),
                f.targetId(), f.suggestion() == null ? null : text.text(f.suggestion()));
    }

    private static PlacementResponse placement(ViewPlacements p) {
        return new PlacementResponse(p.viewId().value(), p.elementIds().stream().map(e -> e.value()).toList(),
                p.relationshipIds().stream().map(r -> r.value()).toList());
    }

    static List<PropertyResponse> properties(List<PropertyEntry> entries) {
        return entries.stream().sorted(Comparator.comparing(PropertyEntry::sortOrder))
                .map(p -> new PropertyResponse(p.key(), p.value())).toList();
    }
}
