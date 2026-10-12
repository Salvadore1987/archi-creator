package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;

/**
 * Размещение объекта на представлении.
 *
 * @param parentId  узел, в который вложен этот; пусто — корень представления
 * @param label     собственная подпись узла — у группы, объектов скетча и холста; у узла
 *                  над элементом подписью служит имя элемента, и своей обычно нет
 * @param content   текст заметки как есть, с переводами строк
 * @param sortOrder позиция в содержимом родителя, общем с рёбрами, исходящими из родителя
 */
public record ViewNode(ViewNodeId id, Optional<ViewNodeId> parentId, ArchiId archiId, DiagramType archiType,
                       Optional<ElementId> elementId, Bounds bounds, StyleOverride style, Optional<String> label,
                       Optional<String> content, SortOrder sortOrder, Optional<RawXml> rawXml) {

    /** Узел без собственного текста — так размещается элемент. */
    public ViewNode(ViewNodeId id, Optional<ViewNodeId> parentId, ArchiId archiId, DiagramType archiType,
                    Optional<ElementId> elementId, Bounds bounds, StyleOverride style, SortOrder sortOrder,
                    Optional<RawXml> rawXml) {
        this(id, parentId, archiId, archiType, elementId, bounds, style, Optional.empty(), Optional.empty(),
                sortOrder, rawXml);
    }

    public ViewNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Objects.requireNonNull(rawXml, "rawXml");
        ViewNodeKind kind = ViewNodeKind.of(archiType);
        if (kind == ViewNodeKind.DIAGRAM_OBJECT && elementId.isEmpty()) {
            throw new InvalidValueException(ModelingMessages.NODE_WITHOUT_ELEMENT, archiId);
        }
        if ((kind == ViewNodeKind.GROUP || kind == ViewNodeKind.NOTE) && elementId.isPresent()) {
            throw new InvalidValueException(ModelingMessages.NODE_WITH_ELEMENT, archiId, kind);
        }
        if (parentId.filter(p -> p.equals(id)).isPresent()) {
            throw new InvalidValueException(ModelingMessages.NODE_SELF_PARENT, archiId);
        }
    }

    public ViewNodeKind kind() {
        return ViewNodeKind.of(archiType);
    }

    public ViewNode withBounds(Bounds newBounds) {
        return new ViewNode(id, parentId, archiId, archiType, elementId, newBounds, style, label, content, sortOrder,
                rawXml);
    }
}
