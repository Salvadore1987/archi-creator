package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;

/**
 * Отрисовка связи на представлении (INV-MDL-008). В файле ребро — {@code sourceConnection}
 * своего источника, поэтому {@code sortOrder} — позиция в содержимом источника.
 *
 * @param relationshipId пусто у соединений, не отражающих связь модели (заметка → элемент)
 */
public record ViewEdge(ViewEdgeId id, ArchiId archiId, DiagramType archiType, Optional<RelationshipId> relationshipId,
                       ViewEndpoint source, ViewEndpoint target, List<Bendpoint> bendpoints, StyleOverride style,
                       SortOrder sortOrder, Optional<RawXml> rawXml) {

    public ViewEdge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(relationshipId, "relationshipId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Objects.requireNonNull(rawXml, "rawXml");
        bendpoints = List.copyOf(bendpoints);
        if (source.equals(id) || target.equals(id)) {
            throw new IllegalArgumentException("ребро " + archiId + " не может быть своим концом");
        }
    }

    public boolean touches(ViewEndpoint endpoint) {
        return source.equals(endpoint) || target.equals(endpoint);
    }

    public ViewEdge withBendpoints(List<Bendpoint> newBendpoints) {
        return new ViewEdge(id, archiId, archiType, relationshipId, source, target, newBendpoints, style, sortOrder,
                rawXml);
    }
}
