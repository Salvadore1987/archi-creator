package uz.salvadore.hamkorbank.archi.interchange.domain.residue;

import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/**
 * Атрибут в остатке на своём месте среди прочих. Без значения — метка типизированного
 * атрибута: значение придёт из столбца. Со значением — атрибут, которого столбцы не знают.
 */
public record ResidueAttribute(String name, Optional<String> value) {

    public ResidueAttribute {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank()) {
            throw new InvalidValueException(InterchangeMessages.ATTRIBUTE_NAME_EMPTY);
        }
    }

    public static ResidueAttribute placeholder(String name) {
        return new ResidueAttribute(name, Optional.empty());
    }

    public static ResidueAttribute literal(String name, String value) {
        return new ResidueAttribute(name, Optional.of(value));
    }

    public boolean typed() {
        return value.isEmpty();
    }
}
