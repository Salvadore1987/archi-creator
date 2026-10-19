package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Уничтожить удалённую модель физически. */
public record PurgeModelCommand(UUID modelId) {
}
