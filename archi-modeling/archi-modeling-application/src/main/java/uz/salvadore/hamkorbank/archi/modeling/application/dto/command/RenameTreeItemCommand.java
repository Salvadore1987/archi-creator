package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Переименовать папку, элемент, связь или представление. */
public record RenameTreeItemCommand(UUID modelId, UUID itemId, String name) {
}
