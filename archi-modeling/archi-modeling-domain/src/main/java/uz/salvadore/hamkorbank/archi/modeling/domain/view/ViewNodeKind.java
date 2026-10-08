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

    public static ViewNodeKind of(DiagramType type) {
        return switch (type.value()) {
            case "archimate:DiagramObject" -> DIAGRAM_OBJECT;
            case "archimate:Group" -> GROUP;
            case "archimate:Note" -> NOTE;
            default -> OTHER;
        };
    }
}
