package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Предложить авторазметку по графу из браузера.
 *
 * @param strategy имя стратегии из контракта; {@code null} — по умолчанию
 */
public record ProposeViewLayoutQuery(UUID viewId, @Nullable String strategy, List<Node> nodes, List<Edge> edges) {

    public ProposeViewLayoutQuery {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /**
     * Узел графа разметки.
     *
     * @param parentId    родитель; {@code null} — корень
     * @param conceptType тип элемента в нотации Archi; {@code null} — у группы и заметки
     */
    public record Node(UUID id, @Nullable UUID parentId, @Nullable String conceptType, int x, int y, int width,
                       int height) {
    }

    /** Ребро графа разметки; конец — узел из этого графа или другое ребро. */
    public record Edge(UUID id, UUID sourceId, UUID targetId) {
    }
}
