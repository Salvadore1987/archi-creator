package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Геометрия узла относительно родителя: у корневых — относительно представления,
 * у детей группы — относительно группы. {@code -1} в размере — «по
 * умолчанию» Archi: так записан узел, размер которого не меняли.
 */
public record Bounds(int x, int y, int width, int height) {

    public static final int DEFAULT_SIZE = -1;
    /** Размер нового узла элемента, когда клиент его не задал, — как у Archi. */
    public static final int ELEMENT_WIDTH = 120;
    public static final int ELEMENT_HEIGHT = 55;

    public Bounds {
        if (!validSize(width) || !validSize(height)) {
            throw new InvalidValueException(ModelingMessages.NODE_SIZE, width, height);
        }
    }

    /** Узел нового размещения: незаданный размер — размер элемента Archi по умолчанию. */
    public static Bounds ofNewElement(int x, int y, Optional<Integer> width, Optional<Integer> height) {
        return new Bounds(x, y, width.orElse(ELEMENT_WIDTH), height.orElse(ELEMENT_HEIGHT));
    }

    public static boolean validSize(int size) {
        return size > 0 || size == DEFAULT_SIZE;
    }
}
