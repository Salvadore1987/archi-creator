package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Objects;

/** Свойство {@code key/value} объекта модели в порядке файла. */
public record PropertyEntry(String key, String value, SortOrder sortOrder) {

    public static final int KEY_MAX = 200;

    public PropertyEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(sortOrder, "sortOrder");
        if (key.length() > KEY_MAX) {
            throw new IllegalArgumentException("ключ свойства длиннее " + KEY_MAX + " символов");
        }
    }
}
