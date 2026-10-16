package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.util.Objects;
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
public record DocumentDefect(String code, Message message, String archiId, Integer line, Integer column) {

    /** Файл не разбирается как XML. */
    public static final String MALFORMED_XML = "IXC_MALFORMED_XML";
    /** Корень — не {@code archimate:model}. */
    public static final String NOT_ARCHIMATE_MODEL = "IXC_NOT_ARCHIMATE_MODEL";
    /** Нет обязательного атрибута: {@code id}, {@code xsi:type}. */
    public static final String MISSING_ATTRIBUTE = "IXC_MISSING_ATTRIBUTE";
    /** Идентификатор не годится для атрибута {@code id}. */
    public static final String INVALID_ID = "IXC_INVALID_ID";
    /** Идентификатор повторяется внутри файла. */
    public static final String DUPLICATE_ID = "IXC_DUPLICATE_ID";
    /** Ссылка на идентификатор, которого в файле нет. */
    public static final String DANGLING_REFERENCE = "IXC_DANGLING_REFERENCE";
    /** У связи или соединения нет одного из концов. */
    public static final String MISSING_END = "IXC_MISSING_END";
    /** {@code targetConnections} перечисляет не те соединения, что ведут к узлу. */
    public static final String TARGET_CONNECTIONS_MISMATCH = "IXC_TARGET_CONNECTIONS_MISMATCH";
    /** Текст там, где ждут только узлы: его некуда положить без потери. */
    public static final String UNEXPECTED_TEXT = "IXC_UNEXPECTED_TEXT";
    /** Узлы вложены глубже предела разбора. */
    public static final String NESTING_TOO_DEEP = "IXC_NESTING_TOO_DEEP";

    public DocumentDefect {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    static DocumentDefect at(String code, Message message, String archiId, int line) {
        return new DocumentDefect(code, message, archiId, lineOf(line), null);
    }

    private static Integer lineOf(int line) {
        return line > 0 ? line : null;
    }
}
