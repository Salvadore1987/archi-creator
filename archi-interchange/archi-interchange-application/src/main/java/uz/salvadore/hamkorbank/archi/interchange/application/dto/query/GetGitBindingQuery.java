package uz.salvadore.hamkorbank.archi.interchange.application.dto.query;

import java.util.Objects;
import java.util.UUID;

/** Текущая привязка рабочего пространства к репозиторию. */
public record GetGitBindingQuery(UUID workspaceId) {

    public GetGitBindingQuery {
        Objects.requireNonNull(workspaceId, "workspaceId");
    }
}
