package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Удалить элемент. */
public record DeleteElementCommand(UUID elementId) {
}
