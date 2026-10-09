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

    public record WorkspaceDto(UUID id, String name) {
    }

    public record ModelSummary(UUID id, UUID workspaceId, String archiId, String name, String documentation,
                               String status, String archiVersion, String createdBy, Instant createdAt,
                               Instant updatedAt) {
    }

    public record PropertyDto(String key, String value) {
    }

    public record FolderDto(UUID id, UUID parentId, String archiId, String name, String folderType, long sortOrder) {
    }

    public record ElementDto(UUID id, UUID folderId, String archiId, String archiType, String layer, String name,
                             String documentation, List<PropertyDto> properties, boolean supported, long sortOrder) {
    }

    public record RelationshipDto(UUID id, UUID folderId, String archiId, String archiType, UUID sourceId,
                                  UUID targetId, String name, String documentation, String accessType,
                                  Boolean directed, List<PropertyDto> properties, boolean supported, long sortOrder,
                                  UUID edgeId) {
    }

    public record ViewSummary(UUID id, UUID folderId, String archiId, String archiType, String name, long sortOrder) {
    }

    /** Дерево модели: папки, элементы, связи, список представлений (OpenModel). */
    public record ModelTree(ModelSummary model, List<FolderDto> folders, List<ElementDto> elements,
                            List<RelationshipDto> relationships, List<ViewSummary> views) {
    }

    public record StyleDto(String fillColor, String font, String fontColor, String lineColor, Integer textAlignment) {
    }

    public record ViewNodeDto(UUID id, UUID parentId, String archiId, String archiType, String kind, UUID elementId,
                              int x, int y, int width, int height, StyleDto style, long sortOrder) {
    }

    public record BendpointDto(int startX, int startY, int endX, int endY) {
    }

    public record ViewEdgeDto(UUID id, String archiId, String archiType, UUID relationshipId, UUID sourceId,
                              UUID targetId, List<BendpointDto> bendpoints, StyleDto style, long sortOrder) {
    }

    /** Payload представления: узлы, рёбра, геометрия (OpenView). */
    public record ViewPayload(UUID id, UUID modelId, UUID folderId, String archiId, String archiType, String name,
                              String documentation, String viewpoint, List<PropertyDto> properties, boolean editable,
                              List<ViewNodeDto> nodes, List<ViewEdgeDto> edges) {
    }

    public record VersionInfo(long versionNo, String author, String comment, String label, Instant createdAt,
                              boolean snapshotAvailable, String gitSha) {
    }

    public record SaveResult(VersionInfo version, boolean created) {
    }

    public record LockInfo(UUID modelId, String owner, Instant acquiredAt, Instant expiresAt) {
    }

    public record Finding(String severity, String code, String message, String targetKind, String targetId,
                          String suggestion) {
    }

    public record AclEntryDto(String principalType, String principal, String access) {
    }

    public record ElementTypeDto(String archiType, String kind, String layer, String phase, boolean supported) {
    }

    public record RelationTypeDto(String archiType, String type, boolean byDefault) {
    }

    // ── Запросы ─────────────────────────────────────────────────────

    public record CreateModelRequest(String name, UUID workspaceId) {
    }

    public record ModelPatch(String name, String documentation) {
    }

    public record CommentRequest(String comment) {
    }

    public record LabelRequest(String label) {
    }

    /** {@code id} и {@code archiId} задаёт клиент, который ссылается на объект до ответа; без них — сервер. */
    public record CreateElementRequest(String archiType, String name, UUID folderId, UUID id, String archiId) {
    }

    /** {@code properties}: {@code null} — не менять, пустой список — снять все. */
    public record ElementPatch(String name, String documentation, List<PropertyDto> properties) {
    }

    public record EdgePlacementRequest(UUID viewId, UUID sourceNodeId, UUID targetNodeId, UUID edgeId,
                                       String edgeArchiId) {
    }

    public record CreateRelationshipRequest(String archiType, UUID sourceId, UUID targetId, String name,
                                            EdgePlacementRequest view, UUID id, String archiId, UUID folderId,
                                            String accessType, Boolean directed) {
    }

    public record CreateFolderRequest(UUID parentId, String name, UUID id, String archiId) {
    }

    public record RenameRequest(String name) {
    }

    public record MoveRequest(UUID targetFolderId, List<UUID> itemIds) {
    }

    public record ItemsRequest(List<UUID> itemIds) {
    }

    public record CreateViewRequest(String name, UUID folderId, UUID id, String archiId) {
    }

    public record PlaceNodeRequest(UUID elementId, int x, int y, Integer width, Integer height, UUID parentId,
                                   UUID id, String archiId) {
    }

    /** Ребро уже существующей связи: концы — узлы или рёбра того же представления. */
    public record PlaceEdgeRequest(UUID id, String archiId, UUID relationshipId, UUID sourceId, UUID targetId,
                                   List<BendpointDto> bendpoints) {
    }

    public record NodeBounds(UUID id, int x, int y, int width, int height) {
    }

    public record EdgeBendpoints(UUID id, List<BendpointDto> bendpoints) {
    }

    /** Геометрия после перемещений: узлы и точки перегиба одной транзакцией. */
    public record LayoutPatch(List<NodeBounds> nodes, List<EdgeBendpoints> edges) {
    }
}
