package uz.salvadore.hamkorbank.archi.modeling.domain.view.layout;

import java.util.Locale;
import java.util.Optional;

/** Как раскладывать узлы верхнего уровня представления. */
public enum LayoutStrategy {
    /** Полосы по слоям ArchiMate сверху вниз, порядок в полосе — против пересечений. */
    LAYERED,
    /** Ярусы по направлению связей: источник выше цели. */
    HIERARCHY,
    /** Плотная сетка по слоям — меньше пустого места, больше длина связей. */
    COMPACT;

    /** Стратегия по имени из контракта: {@code layered}, {@code hierarchy}, {@code compact}. */
    public static Optional<LayoutStrategy> named(String name) {
        for (LayoutStrategy strategy : values()) {
            if (strategy.name().toLowerCase(Locale.ROOT).equals(name)) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }
}
