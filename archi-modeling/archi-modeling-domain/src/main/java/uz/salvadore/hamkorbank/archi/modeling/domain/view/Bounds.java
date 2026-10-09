package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Геометрия узла относительно родителя: у корневых — относительно представления,
 * у детей группы — относительно группы. {@code -1} в размере — «по
 * умолчанию» Archi: так записан узел, размер которого не меняли.
 */
public record Bounds(int x, int y, int width, int height) {

    public static final int DEFAULT_SIZE = -1;

    public Bounds {
        if (!validSize(width) || !validSize(height)) {
            throw new InvalidValueException(ModelingMessages.NODE_SIZE, width, height);
        }
    }

    public static boolean validSize(int size) {
        return size > 0 || size == DEFAULT_SIZE;
    }
}
