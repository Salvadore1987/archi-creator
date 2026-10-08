package uz.salvadore.hamkorbank.archi.interchange.domain.document;

/**
 * Позиция среди соседей в исходном файле: читатель фиксирует, писатель воспроизводит.
 *
 * <p>Плотная и живёт одно преобразование. Не путать с {@code SortOrder} модели:
 * тот разрежен шагом 1000 и живёт в БД.
 */
public record DocumentOrder(int value) {

    public DocumentOrder {
        if (value < 0) {
            throw new IllegalArgumentException("позиция не может быть отрицательной: " + value);
        }
    }

    public static DocumentOrder of(int value) {
        return new DocumentOrder(value);
    }
}
