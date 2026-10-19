package uz.salvadore.hamkorbank.archi.modeling.application.dto;

/** Точка перегиба ребра: смещения от центров источника и цели, как в Archi. */
public record BendpointValue(int startX, int startY, int endX, int endY) {
}
