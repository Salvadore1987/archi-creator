package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Поставить на представление элемент, группу или заметку.
 *
 * @param kind      вид узла по имени из контракта; {@code null} — узел элемента
 * @param elementId обязателен у узла элемента, запрещён у группы и заметки; {@code null} — не задан
 * @param label     подпись группы; {@code null} — не задана
 * @param content   текст заметки; {@code null} — не задан
 * @param width     ширина; {@code null} — по умолчанию для вида узла
 * @param height    высота; {@code null} — по умолчанию для вида узла
 * @param parentId  родитель; {@code null} — корень представления
 * @param id        ключ, выбранный клиентом; {@code null} — выдаёт сервер
 * @param archiId   идентификатор Archi, выбранный клиентом; {@code null} — выдаёт сервер
 */
public record PlaceViewNodeCommand(UUID viewId, @Nullable String kind, @Nullable UUID elementId, @Nullable String label,
                                   @Nullable String content, int x, int y, @Nullable Integer width,
                                   @Nullable Integer height, @Nullable UUID parentId, @Nullable UUID id,
                                   @Nullable String archiId) {
}
