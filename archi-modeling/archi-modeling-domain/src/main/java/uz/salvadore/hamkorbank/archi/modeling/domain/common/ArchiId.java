package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Внешний идентификатор объекта в файле Archi. Уникален
 * в пределах модели, поэтому первичным ключом не служит.
 *
 * <p>Шаблон широкий намеренно: Archi пишет {@code id-<32 hex>}, эталонная модель —
 * {@code id-<24 hex>}, старые версии — иное. Узкий шаблон отклонил бы настоящий файл.
 */
public record ArchiId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,254}$");

    public ArchiId {
        Objects.requireNonNull(value, "archiId");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("INV-MDL-001: недопустимый идентификатор Archi: '" + value + "'");
        }
    }

    public static ArchiId of(String value) {
        return new ArchiId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
