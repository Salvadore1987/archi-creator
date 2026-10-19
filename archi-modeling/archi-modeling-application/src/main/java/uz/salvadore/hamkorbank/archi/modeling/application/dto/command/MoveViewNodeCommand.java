package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/**
 * Вложить узел в контейнер или вынести в корень; координаты — относительно нового родителя.
 *
 * @param parentId новый родитель; {@code null} — корень представления
 */
public record MoveViewNodeCommand(UUID nodeId, UUID parentId, int x, int y) {
}
