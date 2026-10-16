package uz.salvadore.hamkorbank.archi.modeling.domain.view.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/**
 * Предложение раскладки: новая геометрия узлов верхнего уровня в порядке графа и рёбра,
 * чьи точки перегиба сбрасываются. Дети контейнеров не перечислены — их координаты
 * относительны родителю и не меняются.
 */
public record LayoutProposal(Map<ViewNodeId, Bounds> nodes, List<ViewEdgeId> clearedEdges) {

    public LayoutProposal {
        nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        clearedEdges = List.copyOf(clearedEdges);
    }
}
