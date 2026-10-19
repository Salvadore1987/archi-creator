package uz.salvadore.hamkorbank.archi.interchange.domain.common;

import java.util.Objects;

/**
 * Ключ сущности: типизированная обёртка над значением. Два ключа равны, только если
 * совпадают и тип, и значение, — ключ элемента не равен ключу папки с тем же UUID.
 * Строковое представление — само значение.
 *
 * @param <T> тип значения ключа
 */
public abstract class BaseId<T> {

    private final T value;

    /** Ключ над значением; пустым значение не бывает. */
    protected BaseId(T value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** Значение ключа. */
    public T value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other != null && other.getClass() == getClass() && value.equals(((BaseId<?>) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
