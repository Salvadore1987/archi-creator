package uz.salvadore.hamkorbank.archi.modeling.domain.view.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.Layer;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEndpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/**
 * Авторазметка представления. Раскладываются узлы верхнего уровня: контейнер
 * переносится целиком, его дети относительны ему и остаются на местах.
 *
 * <p>Узлы делятся на полосы — по слою ArchiMate или по ярусу связей — и полосы идут
 * сверху вниз. Порядок внутри полосы подбирается по барицентрам соседей в других
 * полосах: несколько проходов вниз и вверх уменьшают пересечения связей. Полоса
 * шире предела переносится на следующий ряд. Узлы не накладываются: ряд — это
 * отрезок по горизонтали, ряды и полосы — по вертикали.
 *
 * <p>Раскладка детерминирована: при равенстве решает порядок узлов в графе,
 * хеш-таблицы на порядок не влияют.
 */
public final class AutoLayout {

    private static final int ORIGIN = 24;
    private static final int DEFAULT_WIDTH = Bounds.ELEMENT_WIDTH;
    private static final int DEFAULT_HEIGHT = Bounds.ELEMENT_HEIGHT;
    private static final int SWEEPS = 4;
    /** Самый узкий предел ряда у разреженной раскладки — чтобы десяток узлов не встал столбиком. */
    private static final int MIN_ROW_WIDTH = 960;

    private final LayoutGraph graph;
    private final LayoutStrategy strategy;
    private final ArchiTypeRegistry registry = ArchiTypeRegistry.archimate32();
    private final Map<ViewNodeId, LayoutGraph.Node> nodes = new LinkedHashMap<>();
    private final Map<ViewEdgeId, LayoutGraph.Edge> edges = new LinkedHashMap<>();
    private final List<ViewNodeId> roots = new ArrayList<>();
    /** Связи между узлами верхнего уровня, источник → цели, в порядке графа. */
    private final Map<ViewNodeId, List<ViewNodeId>> outgoing = new LinkedHashMap<>();
    private final Map<ViewNodeId, List<ViewNodeId>> neighbours = new LinkedHashMap<>();

    private AutoLayout(LayoutGraph graph, LayoutStrategy strategy) {
        this.graph = graph;
        this.strategy = strategy;
    }

    /** Новая геометрия узлов верхнего уровня по графу и стратегии; сам граф не меняется. */
    public static LayoutProposal propose(LayoutGraph graph, LayoutStrategy strategy) {
        return new AutoLayout(graph, strategy).run();
    }

    private LayoutProposal run() {
        graph.nodes().forEach(n -> nodes.putIfAbsent(n.id(), n));
        graph.edges().forEach(e -> edges.putIfAbsent(e.id(), e));
        for (LayoutGraph.Node node : nodes.values()) {
            if (node.parentId() == null || !nodes.containsKey(node.parentId())) {
                roots.add(node.id());
                outgoing.put(node.id(), new ArrayList<>());
                neighbours.put(node.id(), new ArrayList<>());
            }
        }
        connectRoots();
        List<List<ViewNodeId>> bands = order(bands());
        return new LayoutProposal(place(bands), List.copyOf(edges.keySet()));
    }

    // ── Связи между узлами верхнего уровня ──────────────────────────

    private void connectRoots() {
        for (LayoutGraph.Edge edge : edges.values()) {
            Optional<ViewNodeId> source = endpointNode(edge.source(), 0).map(this::rootOf);
            Optional<ViewNodeId> target = endpointNode(edge.target(), 0).map(this::rootOf);
            if (source.isEmpty() || target.isEmpty() || source.equals(target)) {
                continue;
            }
            outgoing.computeIfAbsent(source.get(), k -> new ArrayList<>()).add(target.get());
            neighbours.computeIfAbsent(source.get(), k -> new ArrayList<>()).add(target.get());
            neighbours.computeIfAbsent(target.get(), k -> new ArrayList<>()).add(source.get());
        }
    }

    /** Узел, к которому примыкает конец: ребро-конец ведёт к узлу своего источника. */
    private Optional<ViewNodeId> endpointNode(ViewEndpoint endpoint, int depth) {
        return switch (endpoint) {
            case ViewNodeId node -> nodes.containsKey(node) ? Optional.of(node) : Optional.empty();
            case ViewEdgeId edgeId -> {
                LayoutGraph.Edge edge = edges.get(edgeId);
                yield edge == null || depth > edges.size() ? Optional.empty() : endpointNode(edge.source(), depth + 1);
            }
        };
    }

    private ViewNodeId rootOf(ViewNodeId nodeId) {
        ViewNodeId current = nodeId;
        Set<ViewNodeId> visited = new HashSet<>();
        while (visited.add(current)) {
            ViewNodeId parent = node(current).parentId();
            if (parent == null || !nodes.containsKey(parent)) {
                return current;
            }
            current = parent;
        }
        return current;
    }

    // ── Полосы ──────────────────────────────────────────────────────

    private List<List<ViewNodeId>> bands() {
        Map<ViewNodeId, Integer> bandOf = strategy == LayoutStrategy.HIERARCHY ? ranks() : layerBands();
        TreeMap<Integer, List<ViewNodeId>> bands = new TreeMap<>();
        roots.forEach(id -> bands.computeIfAbsent(bandOf.get(id), k -> new ArrayList<>()).add(id));
        return new ArrayList<>(bands.values());
    }

    private Map<ViewNodeId, Integer> layerBands() {
        Map<ViewNodeId, Integer> bandOf = new HashMap<>();
        roots.forEach(id -> bandOf.put(id, bandOfLayer(layerOf(id))));
        return bandOf;
    }

    /**
     * Слой узла — по его типу; у группы и иного узла без элемента — слой, которого больше
     * всего среди вложенных, при равенстве верхний.
     */
    private Layer layerOf(ViewNodeId rootId) {
        LayoutGraph.Node root = node(rootId);
        if (root.conceptType() != null) {
            return registry.layerOf(root.conceptType());
        }
        Map<Layer, Integer> counts = new EnumMap<>(Layer.class);
        for (LayoutGraph.Node node : nodes.values()) {
            if (!node.id().equals(rootId) && node.conceptType() != null && rootOf(node.id()).equals(rootId)) {
                counts.merge(registry.layerOf(node.conceptType()), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<Layer, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(e -> -bandOfLayer(e.getKey())))
                .map(Map.Entry::getKey).orElse(Layer.OTHER);
    }

    /**
     * Ярус — длина самого длинного пути от истока. Обратные рёбра циклов отбрасываются
     * обходом в глубину в порядке графа.
     */
    private Map<ViewNodeId, Integer> ranks() {
        Map<ViewNodeId, List<ViewNodeId>> acyclic = new LinkedHashMap<>();
        roots.forEach(id -> acyclic.put(id, new ArrayList<>()));
        Set<ViewNodeId> done = new HashSet<>();
        Set<ViewNodeId> onPath = new HashSet<>();
        List<ViewNodeId> finished = new ArrayList<>();
        for (ViewNodeId root : roots) {
            depthFirst(root, done, onPath, acyclic, finished);
        }
        Map<ViewNodeId, Integer> rank = new HashMap<>();
        roots.forEach(id -> rank.put(id, 0));
        for (int i = finished.size() - 1; i >= 0; i--) {
            ViewNodeId source = finished.get(i);
            for (ViewNodeId target : acyclic.getOrDefault(source, List.of())) {
                rank.merge(target, rank.getOrDefault(source, 0) + 1, Math::max);
            }
        }
        return rank;
    }

    private void depthFirst(ViewNodeId node, Set<ViewNodeId> done, Set<ViewNodeId> onPath,
                            Map<ViewNodeId, List<ViewNodeId>> acyclic, List<ViewNodeId> finished) {
        if (done.contains(node)) {
            return;
        }
        onPath.add(node);
        for (ViewNodeId target : outgoing.getOrDefault(node, List.of())) {
            if (!onPath.contains(target)) {
                acyclic.computeIfAbsent(node, k -> new ArrayList<>()).add(target);
                depthFirst(target, done, onPath, acyclic, finished);
            }
        }
        onPath.remove(node);
        done.add(node);
        finished.add(node);
    }

    // ── Порядок в полосе ────────────────────────────────────────────

    private List<List<ViewNodeId>> order(List<List<ViewNodeId>> bands) {
        Map<ViewNodeId, Integer> bandIndex = new HashMap<>();
        for (int b = 0; b < bands.size(); b++) {
            for (ViewNodeId id : bands.get(b)) {
                bandIndex.put(id, b);
            }
        }
        List<List<ViewNodeId>> current = new ArrayList<>();
        for (List<ViewNodeId> band : bands) {
            current.add(new ArrayList<>(band));
        }
        for (int sweep = 0; sweep < SWEEPS; sweep++) {
            for (int b = 1; b < current.size(); b++) {
                reorder(current, b, bandIndex, true);
            }
            for (int b = current.size() - 2; b >= 0; b--) {
                reorder(current, b, bandIndex, false);
            }
        }
        return current;
    }

    /**
     * Полоса сортируется по среднему относительному положению соседей в полосах выше
     * (проход вниз) или ниже (проход вверх). Узел без таких соседей держит своё место.
     */
    private void reorder(List<List<ViewNodeId>> bands, int b, Map<ViewNodeId, Integer> bandIndex, boolean fromAbove) {
        Map<ViewNodeId, Double> position = new HashMap<>();
        for (List<ViewNodeId> band : bands) {
            for (int i = 0; i < band.size(); i++) {
                position.put(band.get(i), (i + 0.5) / band.size());
            }
        }
        List<ViewNodeId> band = bands.get(b);
        Map<ViewNodeId, Double> barycenter = new HashMap<>();
        for (ViewNodeId id : band) {
            double sum = 0;
            int count = 0;
            for (ViewNodeId other : neighbours.getOrDefault(id, List.of())) {
                Integer otherBand = bandIndex.get(other);
                Double otherPosition = position.get(other);
                if (otherBand != null && otherPosition != null && (fromAbove ? otherBand < b : otherBand > b)) {
                    sum += otherPosition;
                    count++;
                }
            }
            barycenter.put(id, count == 0 ? position.get(id) : sum / count);
        }
        List<ViewNodeId> sorted = new ArrayList<>(band);
        Map<ViewNodeId, Integer> before = new HashMap<>();
        for (int i = 0; i < band.size(); i++) {
            before.put(band.get(i), i);
        }
        sorted.sort(Comparator.<ViewNodeId>comparingDouble(barycenter::get).thenComparingInt(before::get));
        bands.set(b, sorted);
    }

    // ── Размещение ──────────────────────────────────────────────────

    private Map<ViewNodeId, Bounds> place(List<List<ViewNodeId>> bands) {
        Spacing spacing = Spacing.of(strategy);
        int widest = roots.stream().mapToInt(this::width).max().orElse(DEFAULT_WIDTH);
        long area = roots.stream().mapToLong(id -> (long) (width(id) + spacing.column()) * (height(id) + spacing.row()))
                .sum();
        int limit = Math.max(widest, Math.max(spacing.minRowWidth(), (int) Math.round(Math.sqrt(area)
                * spacing.aspect())));

        List<List<List<ViewNodeId>>> rowsOfBands = new ArrayList<>();
        int overall = 0;
        for (List<ViewNodeId> band : bands) {
            List<List<ViewNodeId>> rows = wrap(band, limit, spacing);
            rowsOfBands.add(rows);
            for (List<ViewNodeId> row : rows) {
                overall = Math.max(overall, rowWidth(row, spacing));
            }
        }

        Map<ViewNodeId, Bounds> placed = new HashMap<>();
        int y = ORIGIN;
        for (List<List<ViewNodeId>> rows : rowsOfBands) {
            for (List<ViewNodeId> row : rows) {
                int x = ORIGIN + (spacing.centered() ? (overall - rowWidth(row, spacing)) / 2 : 0);
                int rowHeight = 0;
                for (ViewNodeId id : row) {
                    LayoutGraph.Node node = node(id);
                    placed.put(id, new Bounds(x, y, node.bounds().width(), node.bounds().height()));
                    x += width(id) + spacing.column();
                    rowHeight = Math.max(rowHeight, height(id));
                }
                y += rowHeight + spacing.row();
            }
            y += spacing.band() - spacing.row();
        }
        Map<ViewNodeId, Bounds> ordered = new LinkedHashMap<>();
        roots.forEach(id -> ordered.put(id, placed.get(id)));
        return ordered;
    }

    private List<List<ViewNodeId>> wrap(List<ViewNodeId> band, int limit, Spacing spacing) {
        List<List<ViewNodeId>> rows = new ArrayList<>();
        List<ViewNodeId> row = new ArrayList<>();
        int used = 0;
        for (ViewNodeId id : band) {
            int needed = (row.isEmpty() ? 0 : spacing.column()) + width(id);
            if (!row.isEmpty() && used + needed > limit) {
                rows.add(row);
                row = new ArrayList<>();
                used = 0;
                needed = width(id);
            }
            row.add(id);
            used += needed;
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }

    private int rowWidth(List<ViewNodeId> row, Spacing spacing) {
        return row.stream().mapToInt(this::width).sum() + spacing.column() * (row.size() - 1);
    }

    /** Узел графа; идентификатор всегда взят из самого графа, поэтому узел есть. */
    private LayoutGraph.Node node(ViewNodeId id) {
        return Objects.requireNonNull(nodes.get(id));
    }

    /** Полоса слоя в порядке чтения сверху вниз; мотивация и стратегия делят верхнюю. */
    private static int bandOfLayer(Layer layer) {
        return switch (layer) {
            case MOTIVATION, STRATEGY -> 0;
            case BUSINESS -> 1;
            case APPLICATION -> 2;
            case TECHNOLOGY -> 3;
            case PHYSICAL -> 4;
            case IMPLEMENTATION -> 5;
            case OTHER -> 6;
        };
    }

    private int width(ViewNodeId id) {
        int width = node(id).bounds().width();
        return width > 0 ? width : DEFAULT_WIDTH;
    }

    private int height(ViewNodeId id) {
        int height = node(id).bounds().height();
        return height > 0 ? height : DEFAULT_HEIGHT;
    }

    /**
     * Промежутки стратегии: между узлами ряда, между рядами, между полосами; доля
     * квадрата, которую занимает ряд; центровка рядов.
     */
    private record Spacing(int column, int row, int band, double aspect, int minRowWidth, boolean centered) {

        static Spacing of(LayoutStrategy strategy) {
            return switch (strategy) {
                case LAYERED, HIERARCHY -> new Spacing(36, 36, 72, 2.0, MIN_ROW_WIDTH, true);
                case COMPACT -> new Spacing(24, 24, 36, 1.3, 0, false);
            };
        }
    }
}
