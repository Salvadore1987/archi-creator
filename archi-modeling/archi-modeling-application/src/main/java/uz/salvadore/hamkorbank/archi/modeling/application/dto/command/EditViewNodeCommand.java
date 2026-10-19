package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;

/**
 * Правка подписи, текста и собственного стиля узла.
 *
 * @param label   подпись группы; {@code null} — не менять
 * @param content текст заметки; {@code null} — не менять
 * @param style   стиль целиком; {@code null} — не менять
 */
public record EditViewNodeCommand(UUID nodeId, String label, String content, StyleValue style) {
}
