package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/** Свойство {@code key/value} в таблице {@code *_property}. */
@Embeddable
public class PropertyEmbeddable {

    @Column(name = "sort_order", nullable = false)
    private long sortOrder;

    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "value", nullable = false)
    private String value;

    protected PropertyEmbeddable() {
    }

    public PropertyEmbeddable(long sortOrder, String key, String value) {
        this.sortOrder = sortOrder;
        this.key = key;
        this.value = value;
    }

    public long sortOrder() {
        return sortOrder;
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PropertyEmbeddable p && p.sortOrder == sortOrder && p.key.equals(key)
                && p.value.equals(value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sortOrder, key, value);
    }
}
