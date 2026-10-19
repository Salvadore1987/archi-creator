package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Убрать ребро с представления; связь остаётся в модели. */
public record RemoveViewEdgeCommand(UUID edgeId) {
}
