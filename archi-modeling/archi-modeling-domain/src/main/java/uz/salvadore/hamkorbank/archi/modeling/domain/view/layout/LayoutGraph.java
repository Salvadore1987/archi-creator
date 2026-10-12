package uz.salvadore.hamkorbank.archi.modeling.domain.view.layout;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;

/**
 * Представление глазами раскладки: узлы с родителем, типом концепта и геометрией,
 * рёбра с концами. Порядок узлов — порядок содержимого, он решает при равенстве.
 */
public record LayoutGraph(List<Node> nodes, List<Edge> edges) {

    public LayoutGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** @param conceptType тип элемента узла; пусто — группа, заметка или иное */
    public record Node(UUID id, Optional<UUID> parentId, Optional<ArchiType> conceptType, Bounds bounds) {

        public Node {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(parentId, "parentId");
            Objects.requireNonNull(conceptType, "conceptType");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    /** Концы — узлы или другие рёбра графа. */
    public record Edge(UUID id, UUID sourceId, UUID targetId) {

        public Edge {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(targetId, "targetId");
        }
    }
}
