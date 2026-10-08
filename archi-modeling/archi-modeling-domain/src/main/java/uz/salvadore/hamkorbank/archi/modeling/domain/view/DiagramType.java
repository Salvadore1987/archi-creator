package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * {@code xsi:type} представления, узла или ребра: {@code archimate:DiagramObject},
 * {@code archimate:SketchModel}, {@code canvas:CanvasModelBlock}.
 *
 * <p>Не {@link uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType}:
 * тот — тип концепта ArchiMate и знает лишь пространство {@code archimate:}, а холст
 * Archi пишет свои узлы в {@code canvas:}. Отказ в таком типе сломал бы round-trip
 * на первом же холсте.
 */
public record DiagramType(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z][A-Za-z0-9]*:[A-Za-z][A-Za-z0-9]*$");

    public static final DiagramType DIAGRAM_MODEL = new DiagramType("archimate:ArchimateDiagramModel");
    public static final DiagramType DIAGRAM_OBJECT = new DiagramType("archimate:DiagramObject");
    public static final DiagramType GROUP = new DiagramType("archimate:Group");
    public static final DiagramType NOTE = new DiagramType("archimate:Note");
    public static final DiagramType CONNECTION = new DiagramType("archimate:Connection");

    public DiagramType {
        Objects.requireNonNull(value, "diagramType");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("xsi:type вида префикс:Имя, получено: " + value);
        }
    }

    public static DiagramType of(String value) {
        return new DiagramType(value);
    }

    public static boolean valid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
