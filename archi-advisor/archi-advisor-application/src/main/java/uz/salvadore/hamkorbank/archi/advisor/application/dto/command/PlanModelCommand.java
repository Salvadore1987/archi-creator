package uz.salvadore.hamkorbank.archi.advisor.application.dto.command;

import java.util.Locale;
import java.util.UUID;

/**
 * План фрагмента архитектуры по описанию.
 *
 * @param prompt   описание фрагмента словами автора
 * @param language язык ответа — язык интерфейса автора
 */
public record PlanModelCommand(UUID modelId, String prompt, String idempotencyKey, Locale language) {
}
