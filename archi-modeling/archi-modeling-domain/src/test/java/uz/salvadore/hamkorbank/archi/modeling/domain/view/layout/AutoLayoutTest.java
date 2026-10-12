package uz.salvadore.hamkorbank.archi.modeling.domain.view.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;

class AutoLayoutTest {

    private static final String[] TYPES = {"BusinessActor", "BusinessProcess", "ApplicationComponent",
        "ApplicationService", "DataObject", "Node", "SystemSoftware", "Device", "WorkPackage", "Goal"};

    @ParameterizedTest
    @EnumSource(LayoutStrategy.class)
    @DisplayName("FR-15: одинаковый запрос даёт одинаковую раскладку, узлы не накладываются")
    void deterministicWithoutOverlaps(LayoutStrategy strategy) {
        LayoutGraph graph = randomGraph(new Random(42), 200, 320);

        LayoutProposal first = AutoLayout.propose(graph, strategy);
        LayoutProposal second = AutoLayout.propose(randomGraph(new Random(42), 200, 320), strategy);

        assertEquals(List.copyOf(first.nodes().values()), List.copyOf(second.nodes().values()));
        assertEquals(200 - nested(graph), first.nodes().size(), "только узлы верхнего уровня");
        List<Bounds> boxes = List.copyOf(first.nodes().values());
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                assertFalse(overlap(boxes.get(i), boxes.get(j)), boxes.get(i) + " и " + boxes.get(j));
            }
        }
        assertEquals(graph.edges().size(), first.clearedEdges().size(), "точки перегиба всех рёбер сбрасываются");
    }

    @Test
    @DisplayName("UI-005: бизнес выше приложений, приложения выше технологий; размер узла не меняется")
    void layersGoTopDown() {
        var tech = node("Node", 0, 0);
        var app = node("ApplicationComponent", 0, 0);
        var business = node("BusinessProcess", 0, 0);
        var wide = new LayoutGraph.Node(UUID.randomUUID(), Optional.empty(), type("ApplicationService"),
                new Bounds(500, 500, 300, -1));
        LayoutGraph graph = new LayoutGraph(List.of(tech, app, wide, business), List.of());

        Map<UUID, Bounds> placed = AutoLayout.propose(graph, LayoutStrategy.LAYERED).nodes();

        assertTrue(bottom(placed.get(business.id())) < placed.get(app.id()).y());
        assertTrue(bottom(placed.get(app.id())) < placed.get(tech.id()).y());
        assertEquals(placed.get(app.id()).y(), placed.get(wide.id()).y(), "один слой — одна полоса");
        assertEquals(300, placed.get(wide.id()).width());
        assertEquals(-1, placed.get(wide.id()).height(), "размер по умолчанию остаётся умолчанием");
    }

    @Test
    @DisplayName("FR-15: порядок в полосе убирает пересечение связей")
    void barycentersRemoveCrossing() {
        var a1 = node("BusinessProcess", 0, 0);
        var a2 = node("BusinessProcess", 0, 0);
        var b1 = node("ApplicationService", 0, 0);
        var b2 = node("ApplicationService", 0, 0);
        LayoutGraph graph = new LayoutGraph(List.of(a1, a2, b1, b2),
                List.of(edge(a1, b2), edge(a2, b1)));

        Map<UUID, Bounds> placed = AutoLayout.propose(graph, LayoutStrategy.LAYERED).nodes();

        boolean aOrder = placed.get(a1.id()).x() < placed.get(a2.id()).x();
        boolean bOrder = placed.get(b2.id()).x() < placed.get(b1.id()).x();
        assertEquals(aOrder, bOrder, "a1 над b2, a2 над b1 — без пересечения");
    }

    @Test
    @DisplayName("FR-15: ярусы — источник связи выше цели, цикл не зацикливает раскладку")
    void hierarchyPutsSourcesAbove() {
        var root = node("BusinessActor", 0, 0);
        var middle = node("BusinessRole", 0, 0);
        var leaf = node("BusinessProcess", 0, 0);
        LayoutGraph graph = new LayoutGraph(List.of(leaf, middle, root),
                List.of(edge(root, middle), edge(middle, leaf), edge(leaf, root)));

        Map<UUID, Bounds> placed = AutoLayout.propose(graph, LayoutStrategy.HIERARCHY).nodes();

        // Обход начинается с первого узла графа — leaf; цикл замыкает ребро middle → leaf, оно и отброшено.
        assertTrue(placed.get(leaf.id()).y() < placed.get(root.id()).y());
        assertTrue(placed.get(root.id()).y() < placed.get(middle.id()).y());
    }

    @Test
    @DisplayName("UI-004: контейнер переносится целиком, дети не перечислены; группа — в полосе своего содержимого")
    void containersMoveWhole() {
        var group = new LayoutGraph.Node(UUID.randomUUID(), Optional.empty(), Optional.empty(),
                new Bounds(0, 0, 400, 140));
        var inside = new LayoutGraph.Node(UUID.randomUUID(), Optional.of(group.id()), type("Node"),
                new Bounds(10, 10, 120, 55));
        var business = node("BusinessProcess", 0, 0);
        var app = node("ApplicationComponent", 0, 0);
        LayoutGraph graph = new LayoutGraph(List.of(group, inside, business, app), List.of(edge(app, inside)));

        Map<UUID, Bounds> placed = AutoLayout.propose(graph, LayoutStrategy.LAYERED).nodes();

        assertFalse(placed.containsKey(inside.id()));
        assertTrue(bottom(placed.get(app.id())) < placed.get(group.id()).y(), "группа с узлом технологий — внизу");
    }

    private static LayoutGraph randomGraph(Random random, int nodeCount, int edgeCount) {
        List<LayoutGraph.Node> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            Optional<UUID> parent = i > 10 && random.nextInt(10) == 0
                    ? Optional.of(nodes.get(random.nextInt(i)).id()) : Optional.empty();
            int width = random.nextInt(5) == 0 ? -1 : 80 + random.nextInt(200);
            nodes.add(new LayoutGraph.Node(new UUID(0, i), parent, type(TYPES[random.nextInt(TYPES.length)]),
                    new Bounds(random.nextInt(2000), random.nextInt(2000), width, 40 + random.nextInt(80))));
        }
        List<LayoutGraph.Edge> edges = new ArrayList<>();
        for (int i = 0; i < edgeCount; i++) {
            UUID source = nodes.get(random.nextInt(nodeCount)).id();
            UUID target = i > 0 && random.nextInt(20) == 0 ? edges.get(random.nextInt(i)).id()
                    : nodes.get(random.nextInt(nodeCount)).id();
            edges.add(new LayoutGraph.Edge(new UUID(1, i), source, target));
        }
        return new LayoutGraph(nodes, edges);
    }

    private static long nested(LayoutGraph graph) {
        return graph.nodes().stream().filter(n -> n.parentId().isPresent()).count();
    }

    private static boolean overlap(Bounds a, Bounds b) {
        return a.x() < b.x() + width(b) && b.x() < a.x() + width(a)
                && a.y() < b.y() + height(b) && b.y() < a.y() + height(a);
    }

    private static int width(Bounds b) {
        return b.width() > 0 ? b.width() : Bounds.ELEMENT_WIDTH;
    }

    private static int height(Bounds b) {
        return b.height() > 0 ? b.height() : Bounds.ELEMENT_HEIGHT;
    }

    private static int bottom(Bounds b) {
        return b.y() + height(b);
    }

    private static LayoutGraph.Node node(String type, int x, int y) {
        return new LayoutGraph.Node(UUID.randomUUID(), Optional.empty(), type(type), new Bounds(x, y, 120, 55));
    }

    private static LayoutGraph.Edge edge(LayoutGraph.Node source, LayoutGraph.Node target) {
        return new LayoutGraph.Edge(UUID.randomUUID(), source.id(), target.id());
    }

    private static Optional<ArchiType> type(String simpleName) {
        return Optional.of(ArchiType.ofSimpleName(simpleName));
    }
}
