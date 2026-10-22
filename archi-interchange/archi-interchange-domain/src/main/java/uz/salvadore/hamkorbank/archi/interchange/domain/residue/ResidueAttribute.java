package uz.salvadore.hamkorbank.archi.interchange.domain.residue;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.XmlText;

/**
 * Атрибут в остатке на своём месте среди прочих. Без значения — метка типизированного
 * атрибута: значение придёт из столбца. Со значением — атрибут, которого столбцы не знают.
 *
 * @param value значение атрибута; {@code null} — метка типизированного атрибута
 */
public record ResidueAttribute(String name, @Nullable String value) {

    public ResidueAttribute {
        Objects.requireNonNull(name, "name");
        if (!XmlText.isName(name)) {
            throw new InvalidValueException(InterchangeMessages.ATTRIBUTE_NAME_INVALID, name);
        }
    }

    /** Метка типизированного атрибута: значение придёт из столбца. */
    public static ResidueAttribute placeholder(String name) {
        return new ResidueAttribute(name, null);
    }

    /** Атрибут со значением, которого столбцы не знают. */
    public static ResidueAttribute literal(String name, String value) {
        return new ResidueAttribute(name, Objects.requireNonNull(value, "value"));
    }

    /** Метка типизированного атрибута, а не значение. */
    public boolean typed() {
        return value == null;
    }
}
