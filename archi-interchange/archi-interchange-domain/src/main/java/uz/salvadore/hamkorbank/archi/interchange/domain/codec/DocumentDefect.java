package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.util.Objects;
import java.util.Optional;

/**
 * Повреждение данных в файле с местом: что не так, у какого объекта, на какой строке.
 * Отказ без места превращает импорт в чёрный ящик.
 *
 * @param archiId идентификатор объекта как он записан в файле — строкой, потому что
 *                дефект бывает именно в нём
 */
public record DocumentDefect(String code, String message, Optional<String> archiId,
                             Optional<Integer> line, Optional<Integer> column) {

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

    public DocumentDefect {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(column, "column");
    }

    static DocumentDefect at(String code, String message, String archiId, int line) {
        return new DocumentDefect(code, message, Optional.ofNullable(archiId), lineOf(line), Optional.empty());
    }

    private static Optional<Integer> lineOf(int line) {
        return line > 0 ? Optional.of(line) : Optional.empty();
    }
}
