package uz.salvadore.hamkorbank.archi.advisor.application.dto.command;

import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Ревью модели или одного представления.
 *
 * @param viewId   {@code null} — вся модель
 * @param language язык ответа — язык интерфейса автора
 */
public record ReviewModelCommand(UUID modelId, @Nullable UUID viewId, String idempotencyKey, Locale language) {
}
