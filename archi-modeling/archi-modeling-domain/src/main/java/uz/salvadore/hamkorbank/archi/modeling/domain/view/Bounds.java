package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Геометрия узла относительно родителя: у корневых — относительно представления,
 * у детей группы — относительно группы. {@code -1} в размере — «по
 * умолчанию» Archi: так записан узел, размер которого не меняли.
 */
public record Bounds(int x, int y, int width, int height) {

    /** Размер «по умолчанию» Archi: так записан узел, размер которого не меняли. */
    public static final int DEFAULT_SIZE = -1;
    /** Ширина нового узла элемента, когда клиент её не задал, — как у Archi. */
    public static final int ELEMENT_WIDTH = 120;
    /** Высота нового узла элемента, когда клиент её не задал. */
    public static final int ELEMENT_HEIGHT = 55;
    /** Ширина новой группы, когда клиент её не задал, — как у Archi. */
    public static final int GROUP_WIDTH = 400;
    /** Высота новой группы, когда клиент её не задал. */
    public static final int GROUP_HEIGHT = 140;
    /** Ширина новой заметки, когда клиент её не задал. */
    public static final int NOTE_WIDTH = 185;
    /** Высота новой заметки, когда клиент её не задал. */
    public static final int NOTE_HEIGHT = 80;

    public Bounds {
        if (!validSize(width) || !validSize(height)) {
            throw new InvalidValueException(ModelingMessages.NODE_SIZE, width, height);
        }
    }

    /** Узел нового размещения: незаданный ({@code null}) размер — размер элемента Archi по умолчанию. */
    public static Bounds ofNewElement(int x, int y, @Nullable Integer width, @Nullable Integer height) {
        return new Bounds(x, y, orDefault(width, ELEMENT_WIDTH), orDefault(height, ELEMENT_HEIGHT));
    }

    /** Узел нового размещения любого вида: незаданный ({@code null}) размер — размер Archi для этого вида. */
    public static Bounds ofNew(ViewNodeKind kind, int x, int y, @Nullable Integer width, @Nullable Integer height) {
        return switch (kind) {
            case GROUP -> new Bounds(x, y, orDefault(width, GROUP_WIDTH), orDefault(height, GROUP_HEIGHT));
            case NOTE -> new Bounds(x, y, orDefault(width, NOTE_WIDTH), orDefault(height, NOTE_HEIGHT));
            case DIAGRAM_OBJECT, OTHER -> ofNewElement(x, y, width, height);
        };
    }

    private static int orDefault(@Nullable Integer size, int fallback) {
        return size != null ? size : fallback;
    }

    /** Допустим ли размер: положительный или «по умолчанию». */
    public static boolean validSize(int size) {
        return size > 0 || size == DEFAULT_SIZE;
    }
}
