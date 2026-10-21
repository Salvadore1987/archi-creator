package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Link;
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

    private static Optional<Rect> endpoint(String id, Map<String, Link> links) {
        return Routing.endpoint(id, SHAPES, links);
    }
}
