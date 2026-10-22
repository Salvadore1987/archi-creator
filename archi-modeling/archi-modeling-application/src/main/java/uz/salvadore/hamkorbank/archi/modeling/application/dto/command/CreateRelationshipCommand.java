package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Создать связь и, если просят, её ребро на представлении.
 *
 * @param archiType      тип связи, как его прислал клиент
 * @param sourceId       источник — элемент или связь
 * @param targetId       цель — элемент или связь
 * @param name           имя; {@code null} — без имени
 * @param folderId       папка в поддереве {@code Relations}; {@code null} — его корень
 * @param accessType     вид доступа у Access строкой; {@code null} — не задан
 * @param directed       направленность у Association; {@code null} — не задана
 * @param id             ключ, выбранный клиентом; {@code null} — выдаёт сервер
 * @param archiId        идентификатор Archi, выбранный клиентом; {@code null} — выдаёт сервер
 * @param edge           ребро на представлении; {@code null} — не рисуется
 * @param idempotencyKey ключ повтора; {@code null} — без идемпотентности
 */
public record CreateRelationshipCommand(UUID modelId, String archiType, UUID sourceId, UUID targetId,
                                        @Nullable String name, @Nullable UUID folderId, @Nullable String accessType,
                                        @Nullable Boolean directed, @Nullable UUID id, @Nullable String archiId,
                                        @Nullable Edge edge, @Nullable String idempotencyKey) {

    /**
     * Ребро новой связи: концы — узлы или рёбра представления.
     *
     * @param edgeId      ключ ребра, выбранный клиентом; {@code null} — выдаёт сервер
     * @param edgeArchiId идентификатор Archi ребра, выбранный клиентом; {@code null} — выдаёт сервер
     */
    public record Edge(UUID viewId, UUID sourceEndpoint, UUID targetEndpoint, @Nullable UUID edgeId,
                       @Nullable String edgeArchiId) {
    }
}
