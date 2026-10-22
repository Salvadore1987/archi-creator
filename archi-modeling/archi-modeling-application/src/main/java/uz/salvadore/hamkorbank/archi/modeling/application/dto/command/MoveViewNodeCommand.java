package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Вложить узел в контейнер или вынести в корень; координаты — относительно нового родителя.
 *
 * @param parentId новый родитель; {@code null} — корень представления
 */
public record MoveViewNodeCommand(UUID nodeId, @Nullable UUID parentId, int x, int y) {
}
