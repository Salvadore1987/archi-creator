package uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * DTO modeling по контракту OpenAPI. Остаток XML ({@code raw_xml}) наружу
 * не отдаётся: это внутренний формат interchange.
 * Отсутствующее значение — {@code null}, а не {@code Optional}: так его видит JSON.
 * Ограничения Bean Validation проверяют только форму запроса — поле задано, элемент
 * списка не {@code null}; правила значений проверяют сценарий и домен.
 */
public final class Dtos {

    private Dtos() {
    }

    /** Рабочее пространство: идентификатор и имя. */
    public record WorkspaceDto(UUID id, String name) {
    }

    /** Заголовок модели: имя, описание, статус жизненного цикла, версия Archi, автор и время. */
    public record ModelSummary(UUID id, UUID workspaceId, String archiId, String name, @Nullable String documentation,
                               String status, String archiVersion, String createdBy, Instant createdAt,
                               Instant updatedAt) {
    }

    /** Свойство объекта: ключ и значение. */
    public record PropertyDto(@NotNull String key, @Nullable String value) {
    }

    /** Папка дерева модели; {@code folderType} — только у корневой, {@code parentId} — только у вложенной. */
    public record FolderDto(UUID id, @Nullable UUID parentId, String archiId, String name, @Nullable String folderType,
                            long sortOrder) {
    }

    /** Элемент модели; {@code supported} ложно у типа, который редактор не правит. */
    public record ElementDto(UUID id, UUID folderId, String archiId, String archiType, String layer, String name,
                             @Nullable String documentation, List<PropertyDto> properties, boolean supported,
                             long sortOrder) {
    }

    /** Связь модели; концы — элементы или другие связи, {@code edgeId} — ребро, созданное вместе со связью. */
    public record RelationshipDto(UUID id, UUID folderId, String archiId, String archiType, UUID sourceId,
                                  UUID targetId, @Nullable String name, @Nullable String documentation,
                                  @Nullable String accessType, @Nullable Boolean directed, List<PropertyDto> properties,
                                  boolean supported, long sortOrder, @Nullable UUID edgeId) {
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
    public record StyleDto(@Nullable String fillColor, @Nullable String font, @Nullable String fontColor,
                           @Nullable String lineColor, @Nullable Integer textAlignment) {
    }

    /**
     * @param label   собственная подпись узла — у группы, объектов скетча и холста
     * @param content текст заметки с переводами строк
     */
    public record ViewNodeDto(UUID id, @Nullable UUID parentId, String archiId, String archiType, String kind,
                              @Nullable UUID elementId, int x, int y, int width, int height, @Nullable StyleDto style,
                              @Nullable String label, @Nullable String content, long sortOrder) {
    }

    /** Точка перегиба ребра: смещения от начала и от конца. */
    public record BendpointDto(int startX, int startY, int endX, int endY) {
    }

    /** Ребро представления; концы — узлы или другие рёбра. */
    public record ViewEdgeDto(UUID id, String archiId, String archiType, @Nullable UUID relationshipId, UUID sourceId,
                              UUID targetId, List<BendpointDto> bendpoints, @Nullable StyleDto style, long sortOrder) {
    }

    /** Payload представления: узлы, рёбра, геометрия (OpenView). */
    public record ViewPayload(UUID id, UUID modelId, UUID folderId, String archiId, String archiType, String name,
                              @Nullable String documentation, @Nullable String viewpoint, List<PropertyDto> properties,
                              boolean editable, List<ViewNodeDto> nodes, List<ViewEdgeDto> edges) {
    }

    /** Версия модели в истории; {@code snapshotAvailable} ложно, если снимок очищен. */
    public record VersionInfo(long versionNo, String author, @Nullable String comment, @Nullable String label,
                              Instant createdAt, boolean snapshotAvailable, @Nullable String gitSha) {
    }

    /** Итог сохранения: версия и признак, что она новая. */
    public record SaveResult(VersionInfo version, boolean created) {
    }

    /** Блокировка модели: владелец и срок действия. */
    public record LockInfo(UUID modelId, String owner, Instant acquiredAt, Instant expiresAt) {
    }

    /** Находка проверки модели с текстом и подсказкой на языке запроса. */
    public record Finding(String severity, String code, String message, String targetKind, String targetId,
                          @Nullable String suggestion) {
    }

    /** Запись списка доступа: тип субъекта, субъект и уровень доступа. */
    public record AclEntryDto(@NotNull String principalType, @NotNull String principal, @NotNull String access) {
    }

    /** Тип каталога метамодели для палитры; {@code supported} — редактируется ли в текущей фазе. */
    public record ElementTypeDto(String archiType, String kind, String layer, @Nullable String phase,
                                 boolean supported) {
    }

    /** @param nesting эту связь подразумевает вложение цели в источник */
    public record RelationTypeDto(String archiType, String type, boolean byDefault, boolean nesting) {
    }

    // ── Запросы ─────────────────────────────────────────────────────

    /** Создание модели; без {@code workspaceId} — в пространстве по умолчанию. */
    public record CreateModelRequest(String name, @Nullable UUID workspaceId) {
    }

    /** Правка имени и описания модели; отсутствующее поле не меняется. */
    public record ModelPatch(@Nullable String name, @Nullable String documentation) {
    }

    /** Комментарий сохранения или отката. */
    public record CommentRequest(@Nullable String comment) {
    }

    /** Метка версии; {@code null} снимает метку. */
    public record LabelRequest(@Nullable String label) {
    }

    /** {@code id} и {@code archiId} задаёт клиент, который ссылается на объект до ответа; без них — сервер. */
    public record CreateElementRequest(String archiType, String name, @Nullable UUID folderId, @Nullable UUID id,
                                       @Nullable String archiId) {
    }

    /** {@code properties}: {@code null} — не менять, пустой список — снять все. */
    public record ElementPatch(@Nullable String name, @Nullable String documentation,
                               @Nullable List<@NotNull @Valid PropertyDto> properties) {
    }

    /** Ребро, которое рисуется вместе с новой связью; {@code edgeId} и {@code edgeArchiId} может задать клиент. */
    public record EdgePlacementRequest(@NotNull UUID viewId, @NotNull UUID sourceNodeId, @NotNull UUID targetNodeId,
                                       @Nullable UUID edgeId, @Nullable String edgeArchiId) {
    }

    /** Создание связи и, если передан {@code view}, её ребра на представлении. */
    public record CreateRelationshipRequest(String archiType, @NotNull UUID sourceId, @NotNull UUID targetId,
                                            @Nullable String name, @Valid @Nullable EdgePlacementRequest view,
                                            @Nullable UUID id, @Nullable String archiId, @Nullable UUID folderId,
                                            @Nullable String accessType, @Nullable Boolean directed) {
    }

    /** Создание папки в родительской папке. */
    public record CreateFolderRequest(@NotNull UUID parentId, String name, @Nullable UUID id,
                                      @Nullable String archiId) {
    }

    /** Новое имя объекта дерева. */
    public record RenameRequest(String name) {
    }

    /** Перенос объектов дерева в папку. */
    public record MoveRequest(@NotNull UUID targetFolderId, @NotEmpty List<@NotNull UUID> itemIds) {
    }

    /** Объекты дерева для группового удаления. */
    public record ItemsRequest(@NotEmpty List<@NotNull UUID> itemIds) {
    }

    /** Создание представления; без {@code folderId} — в корне представлений. */
    public record CreateViewRequest(String name, @Nullable UUID folderId, @Nullable UUID id, @Nullable String archiId) {
    }

    /**
     * Узел элемента, группа или заметка.
     *
     * @param kind {@code DIAGRAM_OBJECT}, {@code GROUP} или {@code NOTE}; нет — узел элемента
     */
    public record PlaceNodeRequest(@Nullable String kind, @Nullable UUID elementId, @Nullable String label,
                                   @Nullable String content, int x, int y, @Nullable Integer width,
                                   @Nullable Integer height, @Nullable UUID parentId, @Nullable UUID id,
                                   @Nullable String archiId) {
    }

    /** Отсутствующее поле не меняется; стиль заменяется целиком. */
    public record EditNodeRequest(@Nullable String label, @Nullable String content, @Valid @Nullable StyleDto style) {
    }

    /** Новый собственный стиль ребра; заменяется целиком. */
    public record EditEdgeRequest(@Valid @Nullable StyleDto style) {
    }

    /** @param parentId нет — корень представления; координаты — относительно нового родителя */
    public record MoveNodeRequest(@Nullable UUID parentId, int x, int y) {
    }

    /** Ребро уже существующей связи: концы — узлы или рёбра того же представления. */
    public record PlaceEdgeRequest(@Nullable UUID id, @Nullable String archiId, @NotNull UUID relationshipId,
                                   @NotNull UUID sourceId, @NotNull UUID targetId,
                                   @Nullable List<@NotNull BendpointDto> bendpoints) {
    }

    /** Новые границы узла относительно родителя. */
    public record NodeBounds(@NotNull UUID id, int x, int y, int width, int height) {
    }

    /** Новые точки перегиба ребра. */
    public record EdgeBendpoints(@NotNull UUID id, @Nullable List<@NotNull BendpointDto> bendpoints) {
    }

    /** Геометрия после перемещений: узлы и точки перегиба одной транзакцией. */
    public record LayoutPatch(@Nullable List<@NotNull @Valid NodeBounds> nodes,
                              @Nullable List<@NotNull @Valid EdgeBendpoints> edges) {
    }

    /** Граф представления как он в браузере — с несохранёнными узлами. */
    public record AutoLayoutRequest(@Nullable String strategy, @NotNull List<@NotNull @Valid LayoutNode> nodes,
                                    @Nullable List<@NotNull @Valid LayoutEdge> edges) {
    }

    /** @param conceptType тип элемента узла; нет — группа, заметка или иное */
    public record LayoutNode(@NotNull UUID id, @Nullable UUID parentId, @Nullable String conceptType, int x, int y,
                             int width, int height) {
    }

    /** Ребро графа авторазметки между двумя узлами. */
    public record LayoutEdge(@NotNull UUID id, @NotNull UUID sourceId, @NotNull UUID targetId) {
    }
}
