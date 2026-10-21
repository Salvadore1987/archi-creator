package uz.salvadore.hamkorbank.archi.interchange.application.dto.command;

import java.util.Objects;
import java.util.UUID;

/** Опубликовать ожидающие версии модели сейчас. */
public record PushModelToGitCommand(UUID modelId) {

    public PushModelToGitCommand {
        Objects.requireNonNull(modelId, "modelId");
    }
}
