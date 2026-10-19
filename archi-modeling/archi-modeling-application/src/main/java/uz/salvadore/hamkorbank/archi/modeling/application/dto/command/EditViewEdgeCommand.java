package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;

/**
 * Собственный стиль ребра; заменяется целиком.
 *
 * @param style новый стиль; {@code null} — собственного стиля нет
 */
public record EditViewEdgeCommand(UUID edgeId, StyleValue style) {
}
