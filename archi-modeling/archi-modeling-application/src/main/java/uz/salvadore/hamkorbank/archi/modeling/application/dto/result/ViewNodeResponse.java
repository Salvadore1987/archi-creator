package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;

/**
 * Узел представления; границы — относительно родителя.
 *
 * @param parentId  родитель; {@code null} — корень представления
 * @param elementId элемент узла; {@code null} — у группы и заметки
 * @param style     собственный стиль; {@code null} — нет
 */
public record ViewNodeResponse(UUID id, @Nullable UUID parentId, String archiId, String archiType, String kind,
                               @Nullable UUID elementId, int x, int y, int width, int height,
                               @Nullable StyleValue style, @Nullable String label, @Nullable String content,
                               long sortOrder) {
}
