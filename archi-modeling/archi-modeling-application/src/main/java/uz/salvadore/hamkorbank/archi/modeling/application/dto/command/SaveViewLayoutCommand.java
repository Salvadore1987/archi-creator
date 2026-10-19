package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.List;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;

/** Геометрия представления после перемещений: границы узлов и точки перегиба рёбер. */
public record SaveViewLayoutCommand(UUID viewId, List<NodeBounds> nodes, List<EdgeBendpoints> edges) {

    public SaveViewLayoutCommand {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** Границы узла относительно родителя. */
    public record NodeBounds(UUID id, int x, int y, int width, int height) {
    }

    /** Точки перегиба ребра целиком. */
    public record EdgeBendpoints(UUID id, List<BendpointValue> bendpoints) {

        public EdgeBendpoints {
            bendpoints = List.copyOf(bendpoints);
        }
    }
}
