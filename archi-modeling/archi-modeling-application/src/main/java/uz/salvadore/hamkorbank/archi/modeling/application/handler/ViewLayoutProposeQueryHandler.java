package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ProposeViewLayoutQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LayoutProposalResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ViewDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEndpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutGraph;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutStrategy;

/**
 * Предложение авторазметки по графу из браузера; база не меняется. Стратегия — имя
 * из контракта, не задана — раскладка по слоям.
 */
public final class ViewLayoutProposeQueryHandler {

    private final ModelingKernel kernel;
    private final ViewLookup lookup;

    public ViewLayoutProposeQueryHandler(ModelingKernel kernel, ViewLookup lookup) {
        this.kernel = kernel;
        this.lookup = lookup;
    }

    /** Новые границы узлов и рёбра без точек перегиба; предлагается тому, кто может править. */
    public LayoutProposalResponse handle(EditorIdentity actor, ProposeViewLayoutQuery query) {
        return kernel.run(Operation.AUTO_LAYOUT_VIEW, actor, () -> {
            LayoutStrategy strategy = query.strategy() == null ? LayoutStrategy.LAYERED
                    : LayoutStrategy.named(query.strategy()).orElseThrow(() -> ModelingDomainException.invalid(
                            Message.of(ModelingMessages.LAYOUT_STRATEGY_REQUESTED, query.strategy())));
            LayoutGraph graph = graph(query);
            return kernel.unitOfWork().read(() -> {
                View view = lookup.view(query.viewId());
                kernel.visibleHeader(view.modelId(), actor, AclAccess.WRITE);
                return ViewDataMapper.layout(view.proposeLayout(graph, strategy));
            });
        });
    }

    private static LayoutGraph graph(ProposeViewLayoutQuery query) {
        List<LayoutGraph.Node> nodes = query.nodes().stream().map(n -> new LayoutGraph.Node(ViewNodeId.of(n.id()),
                n.parentId() == null ? null : ViewNodeId.of(n.parentId()),
                n.conceptType() == null ? null : RequestValues.archiType(n.conceptType()),
                new Bounds(n.x(), n.y(), n.width(), n.height()))).toList();
        Set<UUID> nodeIds = query.nodes().stream().map(ProposeViewLayoutQuery.Node::id).collect(Collectors.toSet());
        List<LayoutGraph.Edge> edges = query.edges().stream().map(e -> new LayoutGraph.Edge(ViewEdgeId.of(e.id()),
                endpoint(e.sourceId(), nodeIds), endpoint(e.targetId(), nodeIds))).toList();
        return new LayoutGraph(nodes, edges);
    }

    /** Конец ребра в графе разметки: ключ узла из графа — узел, иначе ребро. */
    private static ViewEndpoint endpoint(UUID id, Set<UUID> nodeIds) {
        return nodeIds.contains(id) ? ViewNodeId.of(id) : ViewEdgeId.of(id);
    }
}
