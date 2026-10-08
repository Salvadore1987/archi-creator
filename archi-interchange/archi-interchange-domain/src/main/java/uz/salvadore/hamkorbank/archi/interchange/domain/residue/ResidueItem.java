package uz.salvadore.hamkorbank.archi.interchange.domain.residue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Единица содержимого узла без собственного идентификатора — с позицией {@code order}
 * в той же разреженной нумерации, что и {@code sort_order} дочерних объектов.
 */
public sealed interface ResidueItem permits ResidueItem.Slot, ResidueItem.Value, ResidueItem.Fragment {

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

    /** Значение, которого столбцы не знают, — целиком: тег, атрибуты, текст. */
    record Value(long order, String tag, List<ResidueAttribute> attributes, Optional<String> text)
            implements ResidueItem {

        public Value {
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(text, "text");
            attributes = List.copyOf(attributes);
            if (attributes.stream().anyMatch(ResidueAttribute::typed)) {
                throw new IllegalArgumentException("у нетипизированного значения все атрибуты буквальны");
            }
        }
    }

    /** Непрозрачный фрагмент дословно. */
    record Fragment(long order, String xml) implements ResidueItem {

        public Fragment {
            Objects.requireNonNull(xml, "xml");
        }
    }
}
