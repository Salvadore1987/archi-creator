package uz.salvadore.hamkorbank.archi.modeling.application.dto;

/**
 * Собственный стиль узла или ребра; {@code null} в поле — оформление по токенам слоя.
 *
 * @param textAlignment выравнивание текста в нотации Archi; {@code null} — по умолчанию
 */
public record StyleValue(String fillColor, String font, String fontColor, String lineColor, Integer textAlignment) {
}
