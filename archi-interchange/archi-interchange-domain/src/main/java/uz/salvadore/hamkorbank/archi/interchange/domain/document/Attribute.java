package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/**
 * Атрибут XML с квалифицированным именем как в файле: {@code xsi:type}, {@code name},
 * {@code xmlns:archimate}. Значение — уже без экранирования.
 */
public record Attribute(String name, String value) {

    public Attribute {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank()) {
            throw new InvalidValueException(InterchangeMessages.ATTRIBUTE_NAME_EMPTY);
        }
    }
}
