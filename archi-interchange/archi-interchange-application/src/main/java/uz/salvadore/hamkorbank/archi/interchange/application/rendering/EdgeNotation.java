package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;

/**
 * Оформление связи — семантика нотации, а не стиль: пунктир и наконечники по типу,
 * как на канве. {@code null} — пунктира или наконечника нет.
 */
record EdgeNotation(@Nullable String dash, @Nullable Marker start, @Nullable Marker end) {

    private static final EdgeNotation PLAIN = new EdgeNotation(null, null, null);

    /**
     * Наконечник: путь в собственных координатах, точка крепления к линии ({@code refX}, 5).
     * Фигура лежит на линии до точки крепления; у начала связи она развёрнута наружу.
     */
    enum Marker {
        DIAMOND_FILLED("M1 5 L7 1.4 L13 5 L7 8.6 Z", Fill.EDGE, 13),
        DIAMOND_OPEN("M1 5 L7 1.4 L13 5 L7 8.6 Z", Fill.PAPER, 13),
        DOT("M1.5 5 A3 3 0 1 0 7.5 5 A3 3 0 1 0 1.5 5 Z", Fill.EDGE, 7.5),
        ARROW_FILLED("M1.5 1 L9.5 5 L1.5 9 Z", Fill.EDGE, 9.5),
        ARROW_OPEN("M2.5 1.5 L9.5 5 L2.5 8.5", Fill.NONE, 9.5),
        TRIANGLE_OPEN("M1.5 1 L11.5 5 L1.5 9 Z", Fill.PAPER, 11.5);

        static final double REF_Y = 5;
        static final double STROKE_WIDTH = 1.3;

        final String path;
        final Fill fill;
        final double refX;

        Marker(String path, Fill fill, double refX) {
            this.path = path;
            this.fill = fill;
            this.refX = refX;
        }
    }

    /** Заливка наконечника: цветом связи, цветом бумаги (полый) или никакой (открытая стрелка). */
    enum Fill {
        EDGE,
        PAPER,
        NONE
    }

    /** Оформление по типу связи; {@code accessType} — {@code null}, когда атрибута нет. */
    static EdgeNotation of(String archiType, @Nullable AccessType accessType, boolean directed) {
        return switch (archiType) {
            case "archimate:CompositionRelationship" -> start(Marker.DIAMOND_FILLED);
            case "archimate:AggregationRelationship" -> start(Marker.DIAMOND_OPEN);
            case "archimate:AssignmentRelationship" -> both(null, Marker.DOT, Marker.ARROW_FILLED);
            case "archimate:RealizationRelationship" -> both("6 4", null, Marker.TRIANGLE_OPEN);
            case "archimate:ServingRelationship" -> both(null, null, Marker.ARROW_OPEN);
            // Без атрибута Archi считает доступ записью.
            case "archimate:AccessRelationship" -> switch (accessType != null ? accessType : AccessType.WRITE) {
                case READ -> both("2 3", Marker.ARROW_OPEN, null);
                case READ_WRITE -> both("2 3", Marker.ARROW_OPEN, Marker.ARROW_OPEN);
                case ACCESS -> both("2 3", null, null);
                case WRITE -> both("2 3", null, Marker.ARROW_OPEN);
            };
            case "archimate:InfluenceRelationship" -> both("6 4", null, Marker.ARROW_OPEN);
            case "archimate:TriggeringRelationship" -> both(null, null, Marker.ARROW_FILLED);
            case "archimate:FlowRelationship" -> both("8 4", null, Marker.ARROW_FILLED);
            case "archimate:SpecializationRelationship" -> both(null, null, Marker.TRIANGLE_OPEN);
            case "archimate:AssociationRelationship" -> directed ? both(null, null, Marker.ARROW_OPEN) : PLAIN;
            default -> PLAIN;
        };
    }

    private static EdgeNotation start(Marker marker) {
        return both(null, marker, null);
    }

    private static EdgeNotation both(@Nullable String dash, @Nullable Marker start, @Nullable Marker end) {
        return new EdgeNotation(dash, start, end);
    }
}
