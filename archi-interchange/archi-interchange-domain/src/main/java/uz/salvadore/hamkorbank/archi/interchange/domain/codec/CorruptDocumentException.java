package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.util.List;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Файл повреждён: отказ независимо от строгости импорта.
 * Несёт все найденные дефекты, а не первый: архитектор чинит файл за один проход.
 */
public final class CorruptDocumentException extends InterchangeDomainException {

    private final transient List<DocumentDefect> defects;

    public CorruptDocumentException(List<DocumentDefect> defects) {
        super(InterchangeCodes.CORRUPT_DOCUMENT, summary(requireDefects(defects)));
        this.defects = List.copyOf(defects);
    }

    /** Все найденные дефекты документа. */
    public List<DocumentDefect> defects() {
        return defects;
    }

    /** Первый дефект с местом и число остальных — сообщение с ключом, а не текст. */
    public Message summary() {
        return reason();
    }

    private static List<DocumentDefect> requireDefects(List<DocumentDefect> defects) {
        if (defects.isEmpty()) {
            throw new IllegalArgumentException(Message.of(InterchangeMessages.CORRUPT_WITHOUT_DEFECTS).toString());
        }
        return defects;
    }

    private static Message summary(List<DocumentDefect> defects) {
        DocumentDefect first = defects.getFirst();
        Object place = first.line().<Object>map(l -> Message.of(InterchangeMessages.CORRUPT_AT_LINE, l)).orElse("");
        Object more = defects.size() > 1 ? Message.of(InterchangeMessages.CORRUPT_MORE, defects.size() - 1) : "";
        return Message.of(InterchangeMessages.CORRUPT_SUMMARY, first.code(), first.message(), place, more);
    }
}
