package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveViewLayoutCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ViewDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Геометрия после перемещений одной транзакцией: границы узлов и точки перегиба рёбер. */
public final class ViewLayoutSaveCommandHandler {

    private final ModelingKernel kernel;
    private final ViewLookup lookup;

    public ViewLayoutSaveCommandHandler(ModelingKernel kernel, ViewLookup lookup) {
        this.kernel = kernel;
        this.lookup = lookup;
    }

    /** Геометрия применяется целиком или не применяется вовсе. */
    public void handle(EditorIdentity actor, SaveViewLayoutCommand command) {
        kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> kernel.unitOfWork().write(() -> {
            Map<ViewNodeId, Bounds> nodes = new LinkedHashMap<>();
            command.nodes().forEach(n -> nodes.put(ViewNodeId.of(n.id()),
                    new Bounds(n.x(), n.y(), n.width(), n.height())));
            Map<ViewEdgeId, List<Bendpoint>> edges = new LinkedHashMap<>();
            command.edges().forEach(e -> edges.put(ViewEdgeId.of(e.id()), ViewDataMapper.bendpoints(e.bendpoints())));
            View view = lookup.view(command.viewId());
            kernel.writableModel(view.modelId(), actor);
            view.applyLayout(nodes, edges);
            kernel.views().save(view);
            return null;
        }));
    }
}
