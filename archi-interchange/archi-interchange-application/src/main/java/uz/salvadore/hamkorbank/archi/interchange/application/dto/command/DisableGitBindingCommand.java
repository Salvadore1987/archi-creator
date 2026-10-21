package uz.salvadore.hamkorbank.archi.interchange.application.dto.command;

import java.util.Objects;
import java.util.UUID;

/** Снять привязку рабочего пространства к репозиторию. */
public record DisableGitBindingCommand(UUID workspaceId) {

    public DisableGitBindingCommand {
        Objects.requireNonNull(workspaceId, "workspaceId");
    }
}
