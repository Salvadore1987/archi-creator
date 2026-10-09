package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Collection;
import java.util.Optional;

/**
 * Позиция среди соседей одного родителя — разреженная, шаг {@value #STEP}.
 *
 * <p>Вставка между соседями берёт середину зазора и не трогает остальных:
 * плотная нумерация превращала бы каждую вставку в diff на всю папку при
 * выгрузке в Git. Перебалансировка ветви — только когда зазор исчерпан,
 * и решает о ней вызывающий: {@link #between} отвечает пустым.
 */
public record SortOrder(long value) implements Comparable<SortOrder> {

    public static final long STEP = 1000;

    public SortOrder {
        if (value <= 0) {
            throw new InvalidValueException(ModelingMessages.SORT_ORDER_POSITIVE, value);
        }
    }

    public static SortOrder of(long value) {
        return new SortOrder(value);
    }

    /** Позиция {@code index}-го по счёту (с нуля) при плотной раскладке с шагом. */
    public static SortOrder ofPosition(int index) {
        return new SortOrder((index + 1L) * STEP);
    }

    /** Следующая за последней из занятых; у пустого родителя — первая. */
    public static SortOrder afterLast(Collection<SortOrder> taken) {
        long max = taken.stream().mapToLong(SortOrder::value).max().orElse(0);
        return new SortOrder(max - max % STEP + STEP);
    }

    /** Середина зазора между соседями; пусто — зазор исчерпан, нужна перебалансировка. */
    public static Optional<SortOrder> between(SortOrder before, SortOrder after) {
        long low = before == null ? 0 : before.value;
        if (after.value - low < 2) {
            return Optional.empty();
        }
        return Optional.of(new SortOrder(low + (after.value - low) / 2));
    }

    @Override
    public int compareTo(SortOrder other) {
        return Long.compare(value, other.value);
    }
}
