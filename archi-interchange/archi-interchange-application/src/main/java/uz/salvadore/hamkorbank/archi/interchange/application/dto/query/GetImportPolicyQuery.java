package uz.salvadore.hamkorbank.archi.interchange.application.dto.query;

import java.util.Objects;
import java.util.UUID;

/** Настройка импорта рабочего пространства. */
public record GetImportPolicyQuery(UUID workspaceId) {

    public GetImportPolicyQuery {
        Objects.requireNonNull(workspaceId, "workspaceId");
    }
}
