package uz.salvadore.hamkorbank.archi.modeling.domain.view;

/**
 * Точка перегиба, как в Archi: смещения от центров источника и цели. Поэтому она
 * едет вместе с узлами, а не стоит на холсте.
 */
public record Bendpoint(int startX, int startY, int endX, int endY) {
}
