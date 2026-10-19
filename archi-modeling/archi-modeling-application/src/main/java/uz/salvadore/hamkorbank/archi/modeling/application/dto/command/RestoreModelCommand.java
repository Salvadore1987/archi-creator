package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Вернуть мягко удалённую модель в работу. */
public record RestoreModelCommand(UUID modelId) {
}
