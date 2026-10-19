package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Захватить или продлить блокировку модели на запись. */
public record AcquireLockCommand(UUID modelId) {
}
