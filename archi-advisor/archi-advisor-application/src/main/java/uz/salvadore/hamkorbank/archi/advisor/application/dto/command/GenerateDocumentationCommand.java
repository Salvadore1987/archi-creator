package uz.salvadore.hamkorbank.archi.advisor.application.dto.command;

import java.util.Locale;
import java.util.UUID;

/**
 * Документация по модели, представлению или элементу.
 *
 * @param viewId    {@code null} — не представление
 * @param elementId {@code null} — не элемент; не задано ни то ни другое — вся модель
 * @param language  язык ответа — язык интерфейса автора
 */
public record GenerateDocumentationCommand(UUID modelId, UUID viewId, String elementId, String idempotencyKey,
                                           Locale language) {
}
