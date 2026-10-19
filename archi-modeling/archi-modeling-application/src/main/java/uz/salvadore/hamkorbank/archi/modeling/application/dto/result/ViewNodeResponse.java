package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;

/**
 * Узел представления; границы — относительно родителя.
 *
 * @param parentId  родитель; {@code null} — корень представления
 * @param elementId элемент узла; {@code null} — у группы и заметки
 * @param style     собственный стиль; {@code null} — нет
 */
public record ViewNodeResponse(UUID id, UUID parentId, String archiId, String archiType, String kind, UUID elementId,
                               int x, int y, int width, int height, StyleValue style, String label, String content,
                               long sortOrder) {
}
