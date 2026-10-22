package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.XmlText;

/**
 * Непрозрачный фрагмент: узел, которого кодек не понимает, — дословно и с адресом.
 * Без адреса фрагмент сохранить можно, а воспроизвести — нет. Символы — только XML 1.0, как в тексте
 * и атрибутах: фрагмент приходит и из YAML репозитория, а управляющий символ сделал бы файл нечитаемым.
 *
 * @param xml           разметка фрагмента с внутренними пробелами как в файле;
 *                      префиксы пространств имён — те, что объявлены выше по дереву
 * @param parentArchiId узел, внутри которого фрагмент лежит; {@code null} — прямо в корне модели
 * @param order         позиция среди соседей
 */
public record RawXmlFragment(String xml, @Nullable ArchiId parentArchiId, DocumentOrder order)
        implements DocumentContent {

    public RawXmlFragment {
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(order, "order");
        if (!XmlText.isElement(xml)) {
            throw new InvalidValueException(InterchangeMessages.FRAGMENT_NOT_ELEMENT);
        }
        XmlText.require(xml, "raw_xml");
    }
}
