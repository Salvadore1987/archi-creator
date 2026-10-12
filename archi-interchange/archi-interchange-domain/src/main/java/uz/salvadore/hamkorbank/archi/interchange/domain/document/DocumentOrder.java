package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/**
 * Позиция среди соседей в исходном файле: читатель фиксирует, писатель воспроизводит.
 *
 * <p>Плотная и живёт одно преобразование. Не путать с {@code SortOrder} модели:
 * тот разрежен шагом 1000 и живёт в БД.
 */
public record DocumentOrder(int value) {

    public DocumentOrder {
        if (value < 0) {
            throw new InvalidValueException(InterchangeMessages.ORDER_NEGATIVE, value);
        }
    }

    public static DocumentOrder of(int value) {
        return new DocumentOrder(value);
    }
}
