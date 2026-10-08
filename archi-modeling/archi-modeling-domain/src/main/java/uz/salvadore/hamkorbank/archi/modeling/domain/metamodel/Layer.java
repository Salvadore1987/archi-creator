package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

/**
 * Слой ArchiMate. Выводится из {@link ArchiType}, а не хранится отдельно:
 * у элемента не бывает слоя, расходящегося с его типом.
 *
 * <p>{@link #OTHER} — и для типов вне слоёв ({@code Location}, {@code Grouping},
 * {@code Junction}), и для типов, которых каталог не знает вовсе.
 */
public enum Layer {
    BUSINESS,
    APPLICATION,
    TECHNOLOGY,
    MOTIVATION,
    STRATEGY,
    PHYSICAL,
    IMPLEMENTATION,
    OTHER
}
