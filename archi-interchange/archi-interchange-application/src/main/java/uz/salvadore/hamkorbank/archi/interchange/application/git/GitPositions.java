package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Позиции объектов в уже опубликованном дереве: контейнер (корень модели или папка)
 * → ключ записи → {@code position}. Ключ узла — его идентификатор, ключ значения
 * без идентификатора — вид и номер среди однородных ({@code property#2}).
 *
 * <p>Из них писатель выводит позиции нового дерева так, чтобы неизменённое
 * сохранило свои числа: иначе вставка одного объекта давала бы diff на всю папку.
 */
public record GitPositions(Map<String, Map<String, Long>> byContainer) {

    public GitPositions {
        Map<String, Map<String, Long>> copy = new HashMap<>();
        Objects.requireNonNull(byContainer, "byContainer").forEach((k, v) -> copy.put(k, Map.copyOf(v)));
        byContainer = Map.copyOf(copy);
    }

    /** Дерева ещё нет: позиции — шагом 1000 по порядку документа. */
    public static GitPositions none() {
        return new GitPositions(Map.of());
    }

    Map<String, Long> of(String container) {
        return byContainer.getOrDefault(container, Map.of());
    }
}
