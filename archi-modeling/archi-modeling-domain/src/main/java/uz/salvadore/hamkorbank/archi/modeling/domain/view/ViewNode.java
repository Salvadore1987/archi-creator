package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;

/**
 * Размещение объекта на представлении.
 *
 * @param parentId  узел, в который вложен этот; {@code null} — корень представления
 * @param elementId элемент модели; {@code null} — у группы, заметки и объектов не-ArchiMate
 * @param label     собственная подпись узла — у группы, объектов скетча и холста; у узла
 *                  над элементом подписью служит имя элемента, и своей обычно нет; {@code null} — нет
 * @param content   текст заметки как есть, с переводами строк; {@code null} — нет
 * @param sortOrder позиция в содержимом родителя, общем с рёбрами, исходящими из родителя
 * @param rawXml    остаток XML; {@code null} — нет
 */
public record ViewNode(ViewNodeId id, ViewNodeId parentId, ArchiId archiId, DiagramType archiType,
                       ElementId elementId, Bounds bounds, StyleOverride style, String label,
                       String content, SortOrder sortOrder, RawXml rawXml) {

    /** Узел без собственного текста — так размещается элемент. */
    public ViewNode(ViewNodeId id, ViewNodeId parentId, ArchiId archiId, DiagramType archiType,
                    ElementId elementId, Bounds bounds, StyleOverride style, SortOrder sortOrder,
                    RawXml rawXml) {
        this(id, parentId, archiId, archiType, elementId, bounds, style, null, null,
                sortOrder, rawXml);
    }

    public ViewNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(sortOrder, "sortOrder");
        ViewNodeKind kind = ViewNodeKind.of(archiType);
        if (kind == ViewNodeKind.DIAGRAM_OBJECT && elementId == null) {
            throw new InvalidValueException(ModelingMessages.NODE_WITHOUT_ELEMENT, archiId);
        }
        if ((kind == ViewNodeKind.GROUP || kind == ViewNodeKind.NOTE) && elementId != null) {
            throw new InvalidValueException(ModelingMessages.NODE_WITH_ELEMENT, archiId, kind);
        }
        if (id.equals(parentId)) {
            throw new InvalidValueException(ModelingMessages.NODE_SELF_PARENT, archiId);
        }
    }

    /** Вид узла по его типу в файле. */
    public ViewNodeKind kind() {
        return ViewNodeKind.of(archiType);
    }

    /** Подпись и текст заметки; {@code null} и пустая строка — текста нет. */
    public ViewNode withText(String newLabel, String newContent) {
        return new ViewNode(id, parentId, archiId, archiType, elementId, bounds, style,
                nonEmpty(newLabel), nonEmpty(newContent), sortOrder, rawXml);
    }

    private static String nonEmpty(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** Тот же узел с новым собственным стилем. */
    public ViewNode withStyle(StyleOverride newStyle) {
        return new ViewNode(id, parentId, archiId, archiType, elementId, bounds, newStyle, label, content, sortOrder,
                rawXml);
    }

    /**
     * Узел в другом родителе: новые координаты относительно него и место в его содержимом.
     * {@code null} — корень представления.
     */
    public ViewNode withParent(ViewNodeId newParent, Bounds newBounds, SortOrder newOrder) {
        return new ViewNode(id, newParent, archiId, archiType, elementId, newBounds, style, label, content, newOrder,
                rawXml);
    }

    /** Тот же узел с новой геометрией в прежнем родителе. */
    public ViewNode withBounds(Bounds newBounds) {
        return new ViewNode(id, parentId, archiId, archiType, elementId, newBounds, style, label, content, sortOrder,
                rawXml);
    }
}
