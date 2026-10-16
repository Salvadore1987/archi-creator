package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Set;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Собственный стиль объекта — сильнее токена слоя. Пустое переопределение
 * и его отсутствие — одно и то же. {@code null} в любом поле — значение берётся из токена слоя.
 */
public record StyleOverride(String fillColor, String font, String fontColor,
                            String lineColor, Integer textAlignment) {

    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<Integer> TEXT_ALIGNMENTS = Set.of(1, 2, 4);

    /** Отсутствие собственного стиля: всё берётся из токенов слоя. */
    public static final StyleOverride NONE =
            new StyleOverride(null, null, null, null, null);

    public StyleOverride {
        requireColorIfSet(fillColor, "fillColor");
        requireColorIfSet(fontColor, "fontColor");
        requireColorIfSet(lineColor, "lineColor");
        if (textAlignment != null) {
            requireTextAlignment(textAlignment);
        }
    }

    /** Подходит ли значение как выравнивание текста Archi: 1 — влево, 2 — по центру, 4 — вправо. */
    public static boolean validTextAlignment(int value) {
        return TEXT_ALIGNMENTS.contains(value);
    }

    private static void requireTextAlignment(int value) {
        if (!validTextAlignment(value)) {
            throw new InvalidValueException(ModelingMessages.TEXT_ALIGNMENT_FORMAT, value);
        }
    }

    /** Подходит ли строка как цвет: {@code #} и шесть шестнадцатеричных цифр. */
    public static boolean validColor(String value) {
        return value != null && COLOR.matcher(value).matches();
    }

    private static void requireColorIfSet(String value, String name) {
        if (value != null && !validColor(value)) {
            throw new InvalidValueException(ModelingMessages.COLOR_FORMAT, name, value);
        }
    }

    /** Нет ли переопределений вовсе. */
    public boolean empty() {
        return this.equals(NONE);
    }
}
