package uz.salvadore.hamkorbank.archi.modeling.application.dto;

import org.jspecify.annotations.Nullable;

/**
 * Собственный стиль узла или ребра; {@code null} в поле — оформление по токенам слоя.
 *
 * @param textAlignment выравнивание текста в нотации Archi; {@code null} — по умолчанию
 */
public record StyleValue(@Nullable String fillColor, @Nullable String font, @Nullable String fontColor,
                         @Nullable String lineColor, @Nullable Integer textAlignment) {
}
