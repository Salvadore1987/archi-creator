package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.util.Arrays;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** Вид объекта, часть данных которого не перенеслась в целевой формат. */
public enum LossObjectKind {
    /** Модель целиком. */
    MODEL("model"),
    /** Папка. */
    FOLDER("folder"),
    /** Элемент. */
    ELEMENT("element"),
    /** Связь. */
    RELATIONSHIP("relationship"),
    /** Представление. */
    VIEW("view"),
    /** Узел представления. */
    NODE("node"),
    /** Ребро представления. */
    EDGE("edge"),
    /** Свойство объекта. */
    PROPERTY("property");

    private final String value;

    LossObjectKind(String value) {
        this.value = value;
    }

    /** Вид по значению из отчёта о потерях. */
    public static LossObjectKind of(String value) {
        return Arrays.stream(values()).filter(kind -> kind.value.equals(value)).findFirst()
                .orElseThrow(() -> new InvalidValueException(InterchangeMessages.LOSS_UNKNOWN_KIND,
                        value == null ? Message.of(InterchangeMessages.VALUE_MISSING) : value));
    }

    /** Значение в отчёте о потерях: {@code element}, {@code node}, … */
    public String value() {
        return value;
    }
}
