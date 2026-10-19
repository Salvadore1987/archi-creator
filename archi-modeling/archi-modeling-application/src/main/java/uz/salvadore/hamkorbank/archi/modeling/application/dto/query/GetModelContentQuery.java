package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.UUID;

/** Текущее содержимое модели, как его соберёт сохранение, — основа для замены и сравнения. */
public record GetModelContentQuery(UUID modelId) {
}
