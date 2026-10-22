package uz.salvadore.hamkorbank.archi.interchange.domain.residue;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.XmlText;

/**
 * Единица содержимого узла без собственного идентификатора — с позицией {@code order}
 * в той же разреженной нумерации, что и {@code sort_order} дочерних объектов.
 */
public sealed interface ResidueItem permits ResidueItem.Slot, ResidueItem.Value, ResidueItem.Fragment {

    /** Позиция среди содержимого узла. */
    long order();

    /**
     * Место типизированного значения ({@code documentation}, {@code property}, {@code bounds},
     * {@code bendpoint}, {@code purpose}): значение — в столбце, здесь только позиция и порядок
     * его атрибутов. Значения одного тега занимают свои места по очереди.
     */
    record Slot(long order, String tag, List<ResidueAttribute> attributes) implements ResidueItem {

        public Slot {
            Objects.requireNonNull(tag, "tag");
            attributes = List.copyOf(attributes);
        }
    }

    /**
     * Значение, которого столбцы не знают, — целиком: тег, атрибуты, текст.
     *
     * @param text текст элемента; {@code null} — элемент без текста
     */
    record Value(long order, String tag, List<ResidueAttribute> attributes, @Nullable String text)
            implements ResidueItem {

        public Value {
            Objects.requireNonNull(tag, "tag");
            attributes = List.copyOf(attributes);
            if (attributes.stream().anyMatch(ResidueAttribute::typed)) {
                throw new InvalidValueException(InterchangeMessages.UNTYPED_VALUE_LITERAL);
            }
        }
    }

    /** Непрозрачный фрагмент дословно. */
    record Fragment(long order, String xml) implements ResidueItem {

        public Fragment {
            Objects.requireNonNull(xml, "xml");
            if (!XmlText.isElement(xml)) {
                throw new InvalidValueException(InterchangeMessages.FRAGMENT_NOT_ELEMENT);
            }
        }
    }
}
