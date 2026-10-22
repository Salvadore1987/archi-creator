package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import org.jspecify.annotations.Nullable;

/**
 * Находка проверки модели с текстом на языке запроса.
 *
 * @param severity   {@code ERROR}, {@code WARNING} или {@code INFO}
 * @param suggestion подсказка; {@code null} — нет
 */
public record ValidationFindingResponse(String severity, String code, String message, String targetKind,
                                        String targetId, @Nullable String suggestion) {
}
