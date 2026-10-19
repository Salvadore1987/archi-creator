package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Убрать узел с представления; элемент остаётся в модели. */
public record RemoveViewNodeCommand(UUID nodeId) {
}
