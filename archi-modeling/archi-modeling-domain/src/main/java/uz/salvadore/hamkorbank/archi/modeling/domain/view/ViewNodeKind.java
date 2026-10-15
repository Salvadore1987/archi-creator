package uz.salvadore.hamkorbank.archi.modeling.domain.view;

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
    public java.util.Optional<DiagramType> diagramType() {
        return switch (this) {
            case DIAGRAM_OBJECT -> java.util.Optional.of(DiagramType.DIAGRAM_OBJECT);
            case GROUP -> java.util.Optional.of(DiagramType.GROUP);
            case NOTE -> java.util.Optional.of(DiagramType.NOTE);
            case OTHER -> java.util.Optional.empty();
        };
    }

    /** Вид узла по типу; незнакомый тип — {@link #OTHER}. */
    public static ViewNodeKind of(DiagramType type) {
        return switch (type.value()) {
            case "archimate:DiagramObject" -> DIAGRAM_OBJECT;
            case "archimate:Group" -> GROUP;
            case "archimate:Note" -> NOTE;
            default -> OTHER;
        };
    }
}
