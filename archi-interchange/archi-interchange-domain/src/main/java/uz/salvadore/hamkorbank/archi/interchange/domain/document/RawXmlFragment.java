package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.Objects;
import java.util.Optional;

/**
 * Непрозрачный фрагмент: узел, которого кодек не понимает, — дословно и с адресом
 * (FR-03, INV-IXC-001). Без адреса фрагмент сохранить можно, а воспроизвести — нет.
 *
 * @param xml           разметка фрагмента с внутренними пробелами как в файле;
 *                      префиксы пространств имён — те, что объявлены выше по дереву
 * @param parentArchiId узел, внутри которого фрагмент лежит; пусто — прямо в корне модели
 * @param order         позиция среди соседей
 */
public record RawXmlFragment(String xml, Optional<ArchiId> parentArchiId, DocumentOrder order)
        implements DocumentContent {

    public RawXmlFragment {
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(parentArchiId, "parentArchiId");
        Objects.requireNonNull(order, "order");
        if (!xml.startsWith("<")) {
            throw new IllegalArgumentException("фрагмент обязан быть элементом XML");
        }
    }
}
