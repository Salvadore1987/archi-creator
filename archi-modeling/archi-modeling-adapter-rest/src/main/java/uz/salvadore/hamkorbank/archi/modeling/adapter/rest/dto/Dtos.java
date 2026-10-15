package uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO modeling по контракту OpenAPI. Остаток XML ({@code raw_xml}) наружу
 * не отдаётся: это внутренний формат interchange.
 * Отсутствующее значение — {@code null}, а не {@code Optional}: так его видит JSON.
 */
public final class Dtos {

    private Dtos() {
    }

    /** Рабочее пространство: идентификатор и имя. */
    public record WorkspaceDto(UUID id, String name) {
    }

    /** Заголовок модели: имя, описание, статус жизненного цикла, версия Archi, автор и время. */
    public record ModelSummary(UUID id, UUID workspaceId, String archiId, String name, String documentation,
                               String status, String archiVersion, String createdBy, Instant createdAt,
                               Instant updatedAt) {
    }

    /** Свойство объекта: ключ и значение. */
    public record PropertyDto(String key, String value) {
    }

    /** Папка дерева модели; {@code folderType} — только у корневой, {@code parentId} — только у вложенной. */
    public record FolderDto(UUID id, UUID parentId, String archiId, String name, String folderType, long sortOrder) {
    }

    /** Элемент модели; {@code supported} ложно у типа, который редактор не правит. */
    public record ElementDto(UUID id, UUID folderId, String archiId, String archiType, String layer, String name,
                             String documentation, List<PropertyDto> properties, boolean supported, long sortOrder) {
    }

    /** Связь модели; концы — элементы или другие связи, {@code edgeId} — ребро, созданное вместе со связью. */
    public record RelationshipDto(UUID id, UUID folderId, String archiId, String archiType, UUID sourceId,
                                  UUID targetId, String name, String documentation, String accessType,
                                  Boolean directed, List<PropertyDto> properties, boolean supported, long sortOrder,
                                  UUID edgeId) {
    }

    /** Представление в дереве модели, без содержимого. */
    public record ViewSummary(UUID id, UUID folderId, String archiId, String archiType, String name, long sortOrder) {
    }

    /** Дерево модели: папки, элементы, связи, список представлений и что на них размещено (OpenModel). */
    public record ModelTree(ModelSummary model, List<FolderDto> folders, List<ElementDto> elements,
                            List<RelationshipDto> relationships, List<ViewSummary> views,
                            List<PlacementDto> placements) {
    }

    /** Элементы с узлом и связи с ребром на одном представлении, без повторов. */
    public record PlacementDto(UUID viewId, List<UUID> elementIds, List<UUID> relationshipIds) {
    }

    /** Собственный стиль узла или ребра; незаданное поле берётся из оформления по умолчанию. */
    public record StyleDto(String fillColor, String font, String fontColor, String lineColor, Integer textAlignment) {
    }

    /**
     * @param label   собственная подпись узла — у группы, объектов скетча и холста
     * @param content текст заметки с переводами строк
     */
    public record ViewNodeDto(UUID id, UUID parentId, String archiId, String archiType, String kind, UUID elementId,
                              int x, int y, int width, int height, StyleDto style, String label, String content,
                              long sortOrder) {
    }

    /** Точка перегиба ребра: смещения от начала и от конца. */
    public record BendpointDto(int startX, int startY, int endX, int endY) {
    }

    /** Ребро представления; концы — узлы или другие рёбра. */
    public record ViewEdgeDto(UUID id, String archiId, String archiType, UUID relationshipId, UUID sourceId,
                              UUID targetId, List<BendpointDto> bendpoints, StyleDto style, long sortOrder) {
    }

    /** Payload представления: узлы, рёбра, геометрия (OpenView). */
    public record ViewPayload(UUID id, UUID modelId, UUID folderId, String archiId, String archiType, String name,
                              String documentation, String viewpoint, List<PropertyDto> properties, boolean editable,
                              List<ViewNodeDto> nodes, List<ViewEdgeDto> edges) {
    }

    /** Версия модели в истории; {@code snapshotAvailable} ложно, если снимок очищен. */
    public record VersionInfo(long versionNo, String author, String comment, String label, Instant createdAt,
                              boolean snapshotAvailable, String gitSha) {
    }

    /** Итог сохранения: версия и признак, что она новая. */
    public record SaveResult(VersionInfo version, boolean created) {
    }

    /** Блокировка модели: владелец и срок действия. */
    public record LockInfo(UUID modelId, String owner, Instant acquiredAt, Instant expiresAt) {
    }

    /** Находка проверки модели с текстом и подсказкой на языке запроса. */
    public record Finding(String severity, String code, String message, String targetKind, String targetId,
                          String suggestion) {
    }

    /** Запись списка доступа: тип субъекта, субъект и уровень доступа. */
    public record AclEntryDto(String principalType, String principal, String access) {
    }

    /** Тип каталога метамодели для палитры; {@code supported} — редактируется ли в текущей фазе. */
    public record ElementTypeDto(String archiType, String kind, String layer, String phase, boolean supported) {
    }

    /** @param nesting эту связь подразумевает вложение цели в источник */
    public record RelationTypeDto(String archiType, String type, boolean byDefault, boolean nesting) {
    }

    // ── Запросы ─────────────────────────────────────────────────────

    /** Создание модели; без {@code workspaceId} — в пространстве по умолчанию. */
    public record CreateModelRequest(String name, UUID workspaceId) {
    }

    /** Правка имени и описания модели; отсутствующее поле не меняется. */
    public record ModelPatch(String name, String documentation) {
    }

    /** Комментарий сохранения или отката. */
    public record CommentRequest(String comment) {
    }

    /** Метка версии; {@code null} снимает метку. */
    public record LabelRequest(String label) {
    }

    /** {@code id} и {@code archiId} задаёт клиент, который ссылается на объект до ответа; без них — сервер. */
    public record CreateElementRequest(String archiType, String name, UUID folderId, UUID id, String archiId) {
    }

    /** {@code properties}: {@code null} — не менять, пустой список — снять все. */
    public record ElementPatch(String name, String documentation, List<PropertyDto> properties) {
    }

    /** Ребро, которое рисуется вместе с новой связью; {@code edgeId} и {@code edgeArchiId} может задать клиент. */
    public record EdgePlacementRequest(UUID viewId, UUID sourceNodeId, UUID targetNodeId, UUID edgeId,
                                       String edgeArchiId) {
    }

    /** Создание связи и, если передан {@code view}, её ребра на представлении. */
    public record CreateRelationshipRequest(String archiType, UUID sourceId, UUID targetId, String name,
                                            EdgePlacementRequest view, UUID id, String archiId, UUID folderId,
                                            String accessType, Boolean directed) {
    }

    /** Создание папки в родительской папке. */
    public record CreateFolderRequest(UUID parentId, String name, UUID id, String archiId) {
    }

    /** Новое имя объекта дерева. */
    public record RenameRequest(String name) {
    }

    /** Перенос объектов дерева в папку. */
    public record MoveRequest(UUID targetFolderId, List<UUID> itemIds) {
    }

    /** Объекты дерева для группового удаления. */
    public record ItemsRequest(List<UUID> itemIds) {
    }

    /** Создание представления; без {@code folderId} — в корне представлений. */
    public record CreateViewRequest(String name, UUID folderId, UUID id, String archiId) {
    }

    /**
     * Узел элемента, группа или заметка.
     *
     * @param kind {@code DIAGRAM_OBJECT}, {@code GROUP} или {@code NOTE}; нет — узел элемента
     */
    public record PlaceNodeRequest(String kind, UUID elementId, String label, String content, int x, int y,
                                   Integer width, Integer height, UUID parentId, UUID id, String archiId) {
    }

    /** Отсутствующее поле не меняется; стиль заменяется целиком. */
    public record EditNodeRequest(String label, String content, StyleDto style) {
    }

    /** Новый собственный стиль ребра; заменяется целиком. */
    public record EditEdgeRequest(StyleDto style) {
    }

    /** @param parentId нет — корень представления; координаты — относительно нового родителя */
    public record MoveNodeRequest(UUID parentId, int x, int y) {
    }

    /** Ребро уже существующей связи: концы — узлы или рёбра того же представления. */
    public record PlaceEdgeRequest(UUID id, String archiId, UUID relationshipId, UUID sourceId, UUID targetId,
                                   List<BendpointDto> bendpoints) {
    }

    /** Новые границы узла относительно родителя. */
    public record NodeBounds(UUID id, int x, int y, int width, int height) {
    }

    /** Новые точки перегиба ребра. */
    public record EdgeBendpoints(UUID id, List<BendpointDto> bendpoints) {
    }

    /** Геометрия после перемещений: узлы и точки перегиба одной транзакцией. */
    public record LayoutPatch(List<NodeBounds> nodes, List<EdgeBendpoints> edges) {
    }

    /** Граф представления как он в браузере — с несохранёнными узлами. */
    public record AutoLayoutRequest(String strategy, List<LayoutNode> nodes, List<LayoutEdge> edges) {
    }

    /** @param conceptType тип элемента узла; нет — группа, заметка или иное */
    public record LayoutNode(UUID id, UUID parentId, String conceptType, int x, int y, int width, int height) {
    }

    /** Ребро графа авторазметки между двумя узлами. */
    public record LayoutEdge(UUID id, UUID sourceId, UUID targetId) {
    }
}
