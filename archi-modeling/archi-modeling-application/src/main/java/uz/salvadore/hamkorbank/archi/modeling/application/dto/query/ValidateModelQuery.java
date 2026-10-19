package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.UUID;

/** Отчёт проверки модели по метамодели. */
public record ValidateModelQuery(UUID modelId) {
}
