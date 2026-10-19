package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.List;
import java.util.UUID;

/**
 * Предложение авторазметки: новые границы узлов и рёбра, чьи точки перегиба снимаются.
 */
public record LayoutProposalResponse(List<NodeBounds> nodes, List<UUID> clearedEdges) {

    public LayoutProposalResponse {
        nodes = List.copyOf(nodes);
        clearedEdges = List.copyOf(clearedEdges);
    }

    /** Предложенные границы узла относительно родителя. */
    public record NodeBounds(UUID id, int x, int y, int width, int height) {
    }
}
