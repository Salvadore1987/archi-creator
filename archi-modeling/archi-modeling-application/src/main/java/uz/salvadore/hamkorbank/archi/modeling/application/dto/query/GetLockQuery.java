package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.UUID;

/** Действующая блокировка модели. */
public record GetLockQuery(UUID modelId) {
}
