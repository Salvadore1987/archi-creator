package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/**
 * Что размещено на представлении: элементы, у которых там есть узел, и связи, у которых есть
 * ребро. Каждый — один раз, сколько бы отрисовок у него ни было.
 */
public record ViewPlacements(ViewId viewId, List<ElementId> elementIds, List<RelationshipId> relationshipIds) {

    public ViewPlacements {
        Objects.requireNonNull(viewId, "viewId");
        elementIds = List.copyOf(elementIds);
        relationshipIds = List.copyOf(relationshipIds);
    }

    public static ViewPlacements empty(ViewId viewId) {
        return new ViewPlacements(viewId, List.of(), List.of());
    }
}
