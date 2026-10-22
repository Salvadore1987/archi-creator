package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Сущность: тождество — идентификатор, а не значения полей. Две сущности равны,
 * если совпадают тип и идентификатор, сколько бы ни разошлись их поля.
 *
 * @param <T> тип идентификатора
 */
public abstract class BaseEntity<T> {

    private final T id;

    /** Сущность с идентификатором; пустым он не бывает. */
    protected BaseEntity(T id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    /** Идентификатор сущности. */
    public T id() {
        return id;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other != null && other.getClass() == getClass() && id.equals(((BaseEntity<?>) other).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
