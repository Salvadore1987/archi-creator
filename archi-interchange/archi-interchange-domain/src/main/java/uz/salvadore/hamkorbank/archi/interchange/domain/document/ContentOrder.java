package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.List;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/** Общие проверки и выборки над упорядоченным содержимым узла. */
final class ContentOrder {

    private ContentOrder() {
    }

    /** Позиции идут подряд с нуля: порядок в списке и {@link DocumentOrder} не расходятся. */
    static void requireDense(List<DocumentContent> content) {
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i).order().value() != i) {
                throw new InvalidValueException(InterchangeMessages.ORDER_MISMATCH, content.get(i).order().value(), i);
            }
        }
    }

    static <T extends DocumentContent> List<T> only(List<DocumentContent> content, Class<T> kind) {
        return content.stream().filter(kind::isInstance).map(kind::cast).toList();
    }
}
