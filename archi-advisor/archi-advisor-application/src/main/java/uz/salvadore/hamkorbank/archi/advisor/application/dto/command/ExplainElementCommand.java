package uz.salvadore.hamkorbank.archi.advisor.application.dto.command;

import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Подсказка по элементу и его окрестности.
 *
 * @param elementId идентификатор Archi выбранного элемента
 * @param language  язык ответа — язык интерфейса автора
 */
public record ExplainElementCommand(UUID modelId, @Nullable String elementId, String idempotencyKey, Locale language) {
}
