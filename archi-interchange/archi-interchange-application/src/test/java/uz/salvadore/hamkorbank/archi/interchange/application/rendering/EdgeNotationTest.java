package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.EdgeNotation.Marker;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;

class EdgeNotationTest {

    @Test
    @DisplayName("наконечники и пунктир — по типу связи, как на канве")
    void notationByType() {
        assertEquals(new EdgeNotation(null, Marker.DIAMOND_FILLED, null),
                EdgeNotation.of("archimate:CompositionRelationship", null, false));
        assertEquals(new EdgeNotation(null, Marker.DIAMOND_OPEN, null),
                EdgeNotation.of("archimate:AggregationRelationship", null, false));
        assertEquals(new EdgeNotation(null, Marker.DOT, Marker.ARROW_FILLED),
                EdgeNotation.of("archimate:AssignmentRelationship", null, false));
        assertEquals(new EdgeNotation("6 4", null, Marker.TRIANGLE_OPEN),
                EdgeNotation.of("archimate:RealizationRelationship", null, false));
        assertEquals(new EdgeNotation("8 4", null, Marker.ARROW_FILLED),
                EdgeNotation.of("archimate:FlowRelationship", null, false));
        assertEquals(new EdgeNotation(null, null, Marker.TRIANGLE_OPEN),
                EdgeNotation.of("archimate:SpecializationRelationship", null, false));
    }

    @Test
    @DisplayName("доступ: без атрибута — запись; чтение, чтение и запись, просто доступ — свои наконечники")
    void accessDirections() {
        assertEquals(new EdgeNotation("2 3", null, Marker.ARROW_OPEN),
                EdgeNotation.of("archimate:AccessRelationship", null, false));
        assertEquals(new EdgeNotation("2 3", Marker.ARROW_OPEN, null),
                EdgeNotation.of("archimate:AccessRelationship", AccessType.READ, false));
        assertEquals(new EdgeNotation("2 3", Marker.ARROW_OPEN, Marker.ARROW_OPEN),
                EdgeNotation.of("archimate:AccessRelationship", AccessType.READ_WRITE, false));
        assertEquals(new EdgeNotation("2 3", null, null),
                EdgeNotation.of("archimate:AccessRelationship", AccessType.ACCESS, false));
    }

    @Test
    @DisplayName("ассоциация со стрелкой только направленная; неизвестный тип и простая линия — без оформления")
    void associationAndPlain() {
        assertEquals(new EdgeNotation(null, null, Marker.ARROW_OPEN),
                EdgeNotation.of("archimate:AssociationRelationship", null, true));
        assertEquals(new EdgeNotation(null, null, null),
                EdgeNotation.of("archimate:AssociationRelationship", null, false));
        assertEquals(new EdgeNotation(null, null, null), EdgeNotation.of("archimate:DiagramModelConnection", null,
                false));
    }
}
