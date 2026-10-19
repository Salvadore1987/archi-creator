package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

/** Какие связи предложить между типами источника и цели, как их прислал клиент. */
public record SuggestRelationTypesQuery(String sourceType, String targetType) {
}
