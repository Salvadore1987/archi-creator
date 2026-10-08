package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Идентификатор объекта в файле Archi. Сохраняется буквально, не перегенерируется
 * (INV-IXC-001, ADR-0002).
 *
 * <p>Шаблон широкий намеренно: Archi пишет {@code id-<32 hex>}, эталонная модель —
 * {@code id-<24 hex>}, старые версии и импорт из OEF — иное. Узкий шаблон
 * отклонил бы настоящий файл Archi (FR-01). Проверяется лишь то, что значение
 * пригодно для атрибута {@code id} и не ломает XML.
 */
public record ArchiId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,254}$");

    public ArchiId {
        Objects.requireNonNull(value, "archiId");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("недопустимый идентификатор Archi: '" + value + "'");
        }
    }

    public static ArchiId of(String value) {
        return new ArchiId(value);
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
