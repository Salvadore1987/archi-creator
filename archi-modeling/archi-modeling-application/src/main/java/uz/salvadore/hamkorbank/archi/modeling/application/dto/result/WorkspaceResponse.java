package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.UUID;

/** Рабочее пространство: ключ и название. */
public record WorkspaceResponse(UUID id, String name) {
}
