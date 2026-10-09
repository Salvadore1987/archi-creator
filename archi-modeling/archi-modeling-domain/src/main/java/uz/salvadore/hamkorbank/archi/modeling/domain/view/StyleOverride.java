package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Собственный стиль объекта — сильнее токена слоя. Пустое переопределение
 * и его отсутствие — одно и то же.
 */
public record StyleOverride(Optional<String> fillColor, Optional<String> font, Optional<String> fontColor,
                            Optional<String> lineColor, Optional<Integer> textAlignment) {

    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public static final StyleOverride NONE =
            new StyleOverride(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public StyleOverride {
        Objects.requireNonNull(fillColor, "fillColor");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(fontColor, "fontColor");
        Objects.requireNonNull(lineColor, "lineColor");
        Objects.requireNonNull(textAlignment, "textAlignment");
        fillColor.ifPresent(c -> requireColor(c, "fillColor"));
        fontColor.ifPresent(c -> requireColor(c, "fontColor"));
        lineColor.ifPresent(c -> requireColor(c, "lineColor"));
    }

    public static boolean validColor(String value) {
        return value != null && COLOR.matcher(value).matches();
    }

    private static void requireColor(String value, String name) {
        if (!validColor(value)) {
            throw new InvalidValueException(ModelingMessages.COLOR_FORMAT, name, value);
        }
    }

    public boolean empty() {
        return this.equals(NONE);
    }
}
