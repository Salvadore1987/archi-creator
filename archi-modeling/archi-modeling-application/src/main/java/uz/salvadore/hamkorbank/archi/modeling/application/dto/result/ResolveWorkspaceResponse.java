package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.UUID;

/** Пространство, в котором выполнится команда. */
public record ResolveWorkspaceResponse(UUID workspaceId) {
}
