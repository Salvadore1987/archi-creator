package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Objects;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

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

    /** Диаграмма ArchiMate — единственный вид представления, который редактируется. */
    public static final DiagramType DIAGRAM_MODEL = new DiagramType("archimate:ArchimateDiagramModel");
    /** Узел над элементом модели. */
    public static final DiagramType DIAGRAM_OBJECT = new DiagramType("archimate:DiagramObject");
    /** Группа на представлении. */
    public static final DiagramType GROUP = new DiagramType("archimate:Group");
    /** Заметка на представлении. */
    public static final DiagramType NOTE = new DiagramType("archimate:Note");
    /** Ребро представления. */
    public static final DiagramType CONNECTION = new DiagramType("archimate:Connection");

    public DiagramType {
        Objects.requireNonNull(value, "diagramType");
        if (!Format.PATTERN.matcher(value).matches()) {
            throw new InvalidValueException(ModelingMessages.DIAGRAM_TYPE_FORMAT, value);
        }
    }

    /** Тип из строки; строка не в виде {@code префикс:Имя} отклоняется. */
    public static DiagramType of(String value) {
        return new DiagramType(value);
    }

    /** Подходит ли строка как тип: проверка без исключения. */
    public static boolean valid(String value) {
        return value != null && Format.PATTERN.matcher(value).matches();
    }

    @Override
    public String toString() {
        return value;
    }

    /** Шаблон типа; держатель инициализируется при первой проверке — и до констант выше. */
    private static final class Format {

        static final Pattern PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9]*:[A-Za-z][A-Za-z0-9]*$");

        private Format() {
        }
    }
}
