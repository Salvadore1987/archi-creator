package uz.salvadore.hamkorbank.archi.modeling.domain.view.layout;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEndpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/**
 * Представление глазами раскладки: узлы с родителем, типом концепта и геометрией,
 * рёбра с концами. Порядок узлов — порядок содержимого, он решает при равенстве.
 *
 * <p>Граф приходит от клиента, поэтому проверяется при создании: цепочка родителей
 * не замыкается в цикл. Родитель вне графа допустим — такой узел считается верхним.
 */
public record LayoutGraph(List<Node> nodes, List<Edge> edges) {

    public LayoutGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        requireAcyclicParents(nodes);
    }

    private static void requireAcyclicParents(List<Node> nodes) {
        Map<ViewNodeId, ViewNodeId> parentOf = new HashMap<>();
        for (Node node : nodes) {
            if (node.parentId() != null) {
                parentOf.put(node.id(), node.parentId());
            }
        }
        for (Node node : nodes) {
            Set<ViewNodeId> path = new HashSet<>();
            for (ViewNodeId current = node.id(); current != null; current = parentOf.get(current)) {
                if (!path.add(current)) {
                    throw new InvalidValueException(ModelingMessages.NODE_NESTING_CYCLE, current);
                }
            }
        }
    }

    /**
     * Узел графа.
     *
     * @param parentId    родитель; {@code null} — узел верхнего уровня
     * @param conceptType тип элемента узла; {@code null} — группа, заметка или иное
     */
    public record Node(ViewNodeId id, @Nullable ViewNodeId parentId, @Nullable ArchiType conceptType, Bounds bounds) {

        public Node {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    /** Концы — узлы или другие рёбра графа. */
    public record Edge(ViewEdgeId id, ViewEndpoint source, ViewEndpoint target) {

        public Edge {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }
    }
}
