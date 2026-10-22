package uz.salvadore.hamkorbank.archi.modeling.application.mapper;

import java.util.Comparator;
import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LayoutProposalResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.StyleOverride;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutProposal;

/** Представление и его части — в результаты сценариев; стиль и точки перегиба из команд — в домен. */
public final class ViewDataMapper {

    private ViewDataMapper() {
    }

    /** Заголовок представления — без узлов и рёбер. */
    public static ViewSummaryResponse summary(View view) {
        ViewHeader h = view.header();
        return new ViewSummaryResponse(h.id().value(), h.folderId().value(), h.archiId().value(),
                h.archiType().value(), h.name(), h.sortOrder().value());
    }

    /** Представление целиком; узлы и рёбра — в порядке документа. */
    public static ViewResponse view(View view) {
        ViewHeader h = view.header();
        return new ViewResponse(h.id().value(), h.modelId().value(), h.folderId().value(), h.archiId().value(),
                h.archiType().value(), h.name(), h.documentation(), h.viewpoint(),
                ModelDataMapper.properties(h.properties()), h.editable(),
                view.nodes().values().stream().sorted(Comparator.comparing(ViewNode::sortOrder))
                        .map(ViewDataMapper::node).toList(),
                view.edges().values().stream().sorted(Comparator.comparing(ViewEdge::sortOrder))
                        .map(ViewDataMapper::edge).toList());
    }

    /** Узел; границы — относительно родителя. */
    public static ViewNodeResponse node(ViewNode n) {
        return new ViewNodeResponse(n.id().value(), n.parentId() == null ? null : n.parentId().value(),
                n.archiId().value(), n.archiType().value(), n.kind().name(),
                n.elementId() == null ? null : n.elementId().value(), n.bounds().x(), n.bounds().y(),
                n.bounds().width(), n.bounds().height(), style(n.style()), n.label(), n.content(),
                n.sortOrder().value());
    }

    /** Ребро с точками перегиба и стилем. */
    public static ViewEdgeResponse edge(ViewEdge e) {
        return new ViewEdgeResponse(e.id().value(), e.archiId().value(), e.archiType().value(),
                e.relationshipId() == null ? null : e.relationshipId().value(), e.source().value(),
                e.target().value(),
                e.bendpoints().stream().map(b -> new BendpointValue(b.startX(), b.startY(), b.endX(), b.endY()))
                        .toList(),
                style(e.style()), e.sortOrder().value());
    }

    /** Предложение авторазметки. */
    public static LayoutProposalResponse layout(LayoutProposal proposal) {
        return new LayoutProposalResponse(proposal.nodes().entrySet().stream()
                .map(e -> new LayoutProposalResponse.NodeBounds(e.getKey().value(), e.getValue().x(),
                        e.getValue().y(), e.getValue().width(), e.getValue().height()))
                .toList(),
                proposal.clearedEdges().stream().map(id -> id.value()).toList());
    }

    /** Стиль из команды; {@code null} — собственного стиля нет. */
    public static StyleOverride style(StyleValue s) {
        return s == null ? StyleOverride.NONE
                : new StyleOverride(s.fillColor(), s.font(), s.fontColor(), s.lineColor(), s.textAlignment());
    }

    private static StyleValue style(StyleOverride s) {
        return s.empty() ? null
                : new StyleValue(s.fillColor(), s.font(), s.fontColor(), s.lineColor(), s.textAlignment());
    }

    /** Точки перегиба из команды. */
    public static List<Bendpoint> bendpoints(List<BendpointValue> points) {
        return points.stream().map(b -> new Bendpoint(b.startX(), b.startY(), b.endX(), b.endY())).toList();
    }
}
