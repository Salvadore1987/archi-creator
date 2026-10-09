package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Objects;

/**
 * Остаток XML объекта: то, что не легло в типизированные поля.
 * modeling хранит и отдаёт его, но не разбирает — формат принадлежит interchange.
 */
public record RawXml(String value) {

    public RawXml {
        Objects.requireNonNull(value, "rawXml");
        if (value.isEmpty()) {
            throw new InvalidValueException(ModelingMessages.RESIDUE_EMPTY);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
