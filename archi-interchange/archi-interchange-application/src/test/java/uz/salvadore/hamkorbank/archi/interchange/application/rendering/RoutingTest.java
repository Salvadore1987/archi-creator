package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Bendpoint;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Link;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Point;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Rect;

class RoutingTest {

    private static final Map<String, Rect> SHAPES = Map.of(
            "a", new Rect(0, 0, 100, 40),
            "b", new Rect(300, 0, 100, 40),
            "c", new Rect(150, 200, 100, 40));

    @Test
    @DisplayName("связь к связи крепится к середине маршрута связи-конца, как на канве, — и через звено цепочки")
    void linkAnchor() {
        Map<String, Link> links = Map.of(
                "e1", new Link("a", "b", List.of()),
                "e2", new Link("c", "e1", List.of()));

        assertEquals(Optional.of(new Rect(199.5, 19.5, 1, 1)), endpoint("e1", links));
        // Маршрут e2 вертикален: от верха c (y = 200) до низа квадрата e1 (y = 20.5).
        assertEquals(Optional.of(new Rect(199.5, 109.75, 1, 1)), endpoint("e2", links));
        assertEquals(Optional.of(SHAPES.get("a")), endpoint("a", links));
    }

    @Test
    @DisplayName("цикл связей и неизвестный конец — пусто, без зацикливания и исключений")
    void cycleAndUnknownEndAreEmpty() {
        Map<String, Link> links = Map.of(
                "e1", new Link("a", "e2", List.of()),
                "e2", new Link("a", "e1", List.of()),
                "e3", new Link("a", "nowhere", List.of()));

        assertEquals(Optional.empty(), endpoint("e1", links));
        assertEquals(Optional.empty(), endpoint("e3", links));
        assertEquals(Optional.empty(), endpoint("nowhere", links));
    }

    @Test
    @DisplayName("маршрут без точек перегиба: петля, прямой отрезок по общей полосе, вложение, ступенька")
    void routeWithoutBendpoints() {
        Rect a = new Rect(0, 0, 100, 40);
        assertEquals(List.of(new Point(100, 40.0 / 3), new Point(124, 40.0 / 3), new Point(124, 80.0 / 3),
                new Point(100, 80.0 / 3)), Routing.route(a, a, List.of()));
        assertEquals(List.of(new Point(75, 40), new Point(75, 100)),
                Routing.route(a, new Rect(50, 100, 100, 40), List.of()));
        assertEquals(List.of(new Point(0, 0), new Point(70, 70)),
                Routing.route(new Rect(0, 0, 200, 200), new Rect(50, 50, 20, 20), List.of()));
        assertEquals(List.of(new Point(100, 20), new Point(200, 20), new Point(200, 220), new Point(300, 220)),
                Routing.route(a, new Rect(300, 200, 100, 40), List.of()));
    }

    @Test
    @DisplayName("маршрут через точку перегиба: концы — по лучу из центра к ней, как якорь chopbox")
    void routeThroughBendpoint() {
        assertEquals(List.of(new Point(80, 40), new Point(200, 120), new Point(320, 200)),
                Routing.route(new Rect(0, 0, 100, 40), new Rect(300, 200, 100, 40),
                        List.of(new Bendpoint(0, 0, 0, 0))));
    }

    @Test
    @DisplayName("середина ломаной по длине и путь со скруглённым изломом")
    void midpointAndRoundedPath() {
        List<Point> corner = List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10));
        assertEquals(new Point(10, 0), Routing.midpoint(corner));
        assertEquals(new Point(3, 4), Routing.midpoint(List.of(new Point(3, 4))));
        assertEquals("M0 0 L6 0 Q10 0 10 4 L10 10", Routing.roundedPath(corner, 4));
    }

    private static Optional<Rect> endpoint(String id, Map<String, Link> links) {
        return Routing.endpoint(id, SHAPES, links);
    }
}
