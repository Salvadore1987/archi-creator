package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Снять блокировку модели: свою — владельцу, любую — {@code ADMIN}. */
public record ReleaseLockCommand(UUID modelId) {
}
