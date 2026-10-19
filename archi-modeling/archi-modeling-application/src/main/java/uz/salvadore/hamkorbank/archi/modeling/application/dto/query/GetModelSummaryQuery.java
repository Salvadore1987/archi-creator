package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.UUID;

/** Заголовок модели, видимой на чтение. */
public record GetModelSummaryQuery(UUID modelId) {
}
