package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import java.util.Objects;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Тип объекта ArchiMate в нотации файла Archi: {@code archimate:ApplicationComponent}.
 *
 * <p>Намеренно не enum: тип вне текущей фазы метамодели обязан сохраниться
 * и уйти обратно при экспорте. Enum отверг бы его на чтении, и
 * round-trip сломался бы на первом же {@code Capability}. Знает ли метамодель
 * этот тип, отвечает {@link ArchiTypeRegistry}, а не конструктор.
 */
public record ArchiType(String value) {

    private static final String PREFIX = "archimate:";
    private static final Pattern FORMAT = Pattern.compile("^archimate:[A-Za-z]+$");

    public ArchiType {
        Objects.requireNonNull(value, "archiType");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidValueException(ModelingMessages.ARCHI_TYPE_FORMAT, value);
        }
    }

    public static ArchiType of(String value) {
        return new ArchiType(value);
    }

    /** {@code ApplicationComponent} → {@code archimate:ApplicationComponent}. */
    public static ArchiType ofSimpleName(String simpleName) {
        return new ArchiType(PREFIX + simpleName);
    }

    /** Имя без префикса: так тип называется в спецификации ArchiMate и в матрице связей. */
    public String simpleName() {
        return value.substring(PREFIX.length());
    }

    @Override
    public String toString() {
        return value;
    }
}
