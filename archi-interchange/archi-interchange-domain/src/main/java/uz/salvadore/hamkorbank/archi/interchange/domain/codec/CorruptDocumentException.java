package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.util.List;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Файл повреждён: отказ независимо от строгости импорта.
 * Несёт все найденные дефекты, а не первый: архитектор чинит файл за один проход.
 */
public final class CorruptDocumentException extends RuntimeException {

    private final transient List<DocumentDefect> defects;

    public CorruptDocumentException(List<DocumentDefect> defects) {
        super(defects.isEmpty() ? "" : summary(defects).toString());
        if (defects.isEmpty()) {
            throw new IllegalArgumentException(Message.of(InterchangeMessages.CORRUPT_WITHOUT_DEFECTS).toString());
        }
        this.defects = List.copyOf(defects);
    }

    public List<DocumentDefect> defects() {
        return defects;
    }

    /** Первый дефект с местом и число остальных — сообщение с ключом, а не текст. */
    public Message summary() {
        return summary(defects);
    }

    private static Message summary(List<DocumentDefect> defects) {
        DocumentDefect first = defects.getFirst();
        Object place = first.line().<Object>map(l -> Message.of(InterchangeMessages.CORRUPT_AT_LINE, l)).orElse("");
        Object more = defects.size() > 1 ? Message.of(InterchangeMessages.CORRUPT_MORE, defects.size() - 1) : "";
        return Message.of(InterchangeMessages.CORRUPT_SUMMARY, first.code(), first.message(), place, more);
    }
}
