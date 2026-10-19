package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Optional;

/**
 * Вид узла представления, выводится из {@code xsi:type}. Junction на представлении —
 * {@link #DIAGRAM_OBJECT} над элементом-соединителем. {@link #OTHER} — ссылка на
 * представление, узлы скетча и холста, незнакомые типы: хранятся, но не редактируются.
 */
public enum ViewNodeKind {
    DIAGRAM_OBJECT,
    GROUP,
    NOTE,
    OTHER;

    /** Тип узла этого вида в файле; у {@link #OTHER} своего типа нет. */
    public Optional<DiagramType> diagramType() {
        return switch (this) {
            case DIAGRAM_OBJECT -> Optional.of(DiagramType.DIAGRAM_OBJECT);
            case GROUP -> Optional.of(DiagramType.GROUP);
            case NOTE -> Optional.of(DiagramType.NOTE);
            case OTHER -> Optional.empty();
        };
    }

    /** Вид узла по типу; незнакомый тип — {@link #OTHER}. */
    public static ViewNodeKind of(DiagramType type) {
        if (type.equals(DiagramType.DIAGRAM_OBJECT)) {
            return DIAGRAM_OBJECT;
        }
        if (type.equals(DiagramType.GROUP)) {
            return GROUP;
        }
        return type.equals(DiagramType.NOTE) ? NOTE : OTHER;
    }
}
