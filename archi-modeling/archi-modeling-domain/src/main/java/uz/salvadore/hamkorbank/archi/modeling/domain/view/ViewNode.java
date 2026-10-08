package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;

/**
 * Размещение объекта на представлении (INV-MDL-008).
 *
 * @param parentId  узел, в который вложен этот; пусто — корень представления
 * @param sortOrder позиция в содержимом родителя, общем с рёбрами, исходящими из родителя
 */
public record ViewNode(ViewNodeId id, Optional<ViewNodeId> parentId, ArchiId archiId, DiagramType archiType,
                       Optional<ElementId> elementId, Bounds bounds, StyleOverride style, SortOrder sortOrder,
                       Optional<RawXml> rawXml) {

    public ViewNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Objects.requireNonNull(rawXml, "rawXml");
        ViewNodeKind kind = ViewNodeKind.of(archiType);
        if (kind == ViewNodeKind.DIAGRAM_OBJECT && elementId.isEmpty()) {
            throw new IllegalArgumentException("INV-MDL-008: узел " + archiId + " вида DIAGRAM_OBJECT без элемента");
        }
        if ((kind == ViewNodeKind.GROUP || kind == ViewNodeKind.NOTE) && elementId.isPresent()) {
            throw new IllegalArgumentException("INV-MDL-008: у узла " + archiId + " вида " + kind + " элемента нет");
        }
        if (parentId.filter(p -> p.equals(id)).isPresent()) {
            throw new IllegalArgumentException("узел " + archiId + " не может быть своим родителем");
        }
    }

    public ViewNodeKind kind() {
        return ViewNodeKind.of(archiType);
    }

    public ViewNode withBounds(Bounds newBounds) {
        return new ViewNode(id, parentId, archiId, archiType, elementId, newBounds, style, sortOrder, rawXml);
    }
}
