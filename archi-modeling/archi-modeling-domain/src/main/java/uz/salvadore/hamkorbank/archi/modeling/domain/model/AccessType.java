package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Вид доступа у связи Access. В файле — число в атрибуте {@code accessType};
 * отсутствие атрибута Archi читает как {@link #WRITE}.
 */
public enum AccessType {
    WRITE(0),
    READ(1),
    ACCESS(2),
    READ_WRITE(3);

    private final int fileValue;

    AccessType(int fileValue) {
        this.fileValue = fileValue;
    }

    public int fileValue() {
        return fileValue;
    }

    public static Optional<AccessType> fromFileValue(String value) {
        return Arrays.stream(values()).filter(t -> String.valueOf(t.fileValue).equals(value)).findFirst();
    }
}
