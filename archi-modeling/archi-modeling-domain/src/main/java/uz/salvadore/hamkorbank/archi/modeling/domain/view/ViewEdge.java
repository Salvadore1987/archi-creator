package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;

/**
 * Отрисовка связи на представлении. В файле ребро — {@code sourceConnection}
 * своего источника, поэтому {@code sortOrder} — позиция в содержимом источника.
 *
 * @param relationshipId {@code null} у соединений, не отражающих связь модели (заметка → элемент)
 * @param rawXml         остаток XML; {@code null} — нет
 */
public record ViewEdge(ViewEdgeId id, ArchiId archiId, DiagramType archiType, @Nullable RelationshipId relationshipId,
                       ViewEndpoint source, ViewEndpoint target, List<Bendpoint> bendpoints, StyleOverride style,
                       SortOrder sortOrder, @Nullable RawXml rawXml) {

    public ViewEdge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(sortOrder, "sortOrder");
        bendpoints = List.copyOf(bendpoints);
        if (source.equals(id) || target.equals(id)) {
            throw new InvalidValueException(ModelingMessages.EDGE_SELF_END, archiId);
        }
    }

    /** Служит ли узел или ребро концом этого ребра. */
    public boolean touches(ViewEndpoint endpoint) {
        return source.equals(endpoint) || target.equals(endpoint);
    }

    /** То же ребро с новым собственным стилем. */
    public ViewEdge withStyle(StyleOverride newStyle) {
        return new ViewEdge(id, archiId, archiType, relationshipId, source, target, bendpoints, newStyle, sortOrder,
                rawXml);
    }

    /** То же ребро с новыми точками перегиба. */
    public ViewEdge withBendpoints(List<Bendpoint> newBendpoints) {
        return new ViewEdge(id, archiId, archiType, relationshipId, source, target, newBendpoints, style, sortOrder,
                rawXml);
    }
}
