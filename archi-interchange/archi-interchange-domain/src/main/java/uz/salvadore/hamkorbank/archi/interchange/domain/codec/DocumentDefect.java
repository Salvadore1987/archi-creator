package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Повреждение данных в файле с местом: что не так, у какого объекта, на какой строке.
 * Отказ без места превращает импорт в чёрный ящик.
 *
 * @param archiId идентификатор объекта как он записан в файле — строкой, потому что
 *                дефект бывает именно в нём; {@code null} — дефект не у объекта
 * @param line    строка файла; {@code null} — неизвестна
 * @param column  столбец строки; {@code null} — неизвестен
 */
public record DocumentDefect(String code, Message message, @Nullable String archiId, @Nullable Integer line,
        @Nullable Integer column) {

    public DocumentDefect {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    static DocumentDefect at(String code, Message message, @Nullable String archiId, int line) {
        return new DocumentDefect(code, message, archiId, lineOf(line), null);
    }

    private static @Nullable Integer lineOf(int line) {
        return line > 0 ? line : null;
    }
}
