package uz.salvadore.hamkorbank.archi.modeling.domain.view.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.Layer;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;

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

    /** Слои в порядке чтения сверху вниз; мотивация и стратегия делят верхнюю полосу. */
    private static final Map<Layer, Integer> BAND_OF_LAYER = new EnumMap<>(Map.of(
            Layer.MOTIVATION, 0, Layer.STRATEGY, 0, Layer.BUSINESS, 1, Layer.APPLICATION, 2,
            Layer.TECHNOLOGY, 3, Layer.PHYSICAL, 4, Layer.IMPLEMENTATION, 5, Layer.OTHER, 6));

    private final LayoutGraph graph;
    private final LayoutStrategy strategy;
    private final ArchiTypeRegistry registry = ArchiTypeRegistry.archimate32();
    private final Map<UUID, LayoutGraph.Node> nodes = new LinkedHashMap<>();
    private final Map<UUID, LayoutGraph.Edge> edges = new LinkedHashMap<>();
    private final List<UUID> roots = new ArrayList<>();
    /** Связи между узлами верхнего уровня, источник → цели, в порядке графа. */
    private final Map<UUID, List<UUID>> outgoing = new LinkedHashMap<>();
    private final Map<UUID, List<UUID>> neighbours = new LinkedHashMap<>();

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
        List<List<UUID>> bands = order(bands());
        return new LayoutProposal(place(bands), List.copyOf(edges.keySet()));
    }

    // ── Связи между узлами верхнего уровня ──────────────────────────

    private void connectRoots() {
        for (LayoutGraph.Edge edge : edges.values()) {
            Optional<UUID> source = endpointNode(edge.sourceId(), 0).map(this::rootOf);
            Optional<UUID> target = endpointNode(edge.targetId(), 0).map(this::rootOf);
            if (source.isEmpty() || target.isEmpty() || source.equals(target)) {
                continue;
            }
            outgoing.get(source.get()).add(target.get());
            neighbours.get(source.get()).add(target.get());
            neighbours.get(target.get()).add(source.get());
        }
    }

    /** Узел, к которому примыкает конец: ребро-конец ведёт к узлу своего источника. */
    private Optional<UUID> endpointNode(UUID endpoint, int depth) {
        if (nodes.containsKey(endpoint)) {
            return Optional.of(endpoint);
        }
        LayoutGraph.Edge edge = edges.get(endpoint);
        return edge == null || depth > edges.size() ? Optional.empty() : endpointNode(edge.sourceId(), depth + 1);
    }

    private UUID rootOf(UUID nodeId) {
        UUID current = nodeId;
        Set<UUID> visited = new HashSet<>();
        while (visited.add(current)) {
            UUID parent = nodes.get(current).parentId();
            if (parent == null || !nodes.containsKey(parent)) {
                return current;
            }
            current = parent;
        }
        return current;
    }

    // ── Полосы ──────────────────────────────────────────────────────

    private List<List<UUID>> bands() {
        Map<UUID, Integer> bandOf = strategy == LayoutStrategy.HIERARCHY ? ranks() : layerBands();
        TreeMap<Integer, List<UUID>> bands = new TreeMap<>();
        roots.forEach(id -> bands.computeIfAbsent(bandOf.get(id), k -> new ArrayList<>()).add(id));
        return new ArrayList<>(bands.values());
    }

    private Map<UUID, Integer> layerBands() {
        Map<UUID, Integer> bandOf = new HashMap<>();
        roots.forEach(id -> bandOf.put(id, BAND_OF_LAYER.get(layerOf(id))));
        return bandOf;
    }

    /**
     * Слой узла — по его типу; у группы и иного узла без элемента — слой, которого больше
     * всего среди вложенных, при равенстве верхний.
     */
    private Layer layerOf(UUID rootId) {
        LayoutGraph.Node root = nodes.get(rootId);
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
                        .thenComparing(e -> -BAND_OF_LAYER.get(e.getKey())))
                .map(Map.Entry::getKey).orElse(Layer.OTHER);
    }

    /**
     * Ярус — длина самого длинного пути от истока. Обратные рёбра циклов отбрасываются
     * обходом в глубину в порядке графа.
     */
    private Map<UUID, Integer> ranks() {
        Map<UUID, List<UUID>> acyclic = new LinkedHashMap<>();
        roots.forEach(id -> acyclic.put(id, new ArrayList<>()));
        Set<UUID> done = new HashSet<>();
        Set<UUID> onPath = new HashSet<>();
        List<UUID> finished = new ArrayList<>();
        for (UUID root : roots) {
            depthFirst(root, done, onPath, acyclic, finished);
        }
        Map<UUID, Integer> rank = new HashMap<>();
        roots.forEach(id -> rank.put(id, 0));
        for (int i = finished.size() - 1; i >= 0; i--) {
            UUID source = finished.get(i);
            for (UUID target : acyclic.get(source)) {
                rank.merge(target, rank.get(source) + 1, Math::max);
            }
        }
        return rank;
    }

    private void depthFirst(UUID node, Set<UUID> done, Set<UUID> onPath, Map<UUID, List<UUID>> acyclic,
                            List<UUID> finished) {
        if (done.contains(node)) {
            return;
        }
        onPath.add(node);
        for (UUID target : outgoing.get(node)) {
            if (!onPath.contains(target)) {
                acyclic.get(node).add(target);
                depthFirst(target, done, onPath, acyclic, finished);
            }
        }
        onPath.remove(node);
        done.add(node);
        finished.add(node);
    }

    // ── Порядок в полосе ────────────────────────────────────────────

    private List<List<UUID>> order(List<List<UUID>> bands) {
        Map<UUID, Integer> bandIndex = new HashMap<>();
        for (int b = 0; b < bands.size(); b++) {
            for (UUID id : bands.get(b)) {
                bandIndex.put(id, b);
            }
        }
        List<List<UUID>> current = new ArrayList<>();
        for (List<UUID> band : bands) {
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
    private void reorder(List<List<UUID>> bands, int b, Map<UUID, Integer> bandIndex, boolean fromAbove) {
        Map<UUID, Double> position = new HashMap<>();
        for (List<UUID> band : bands) {
            for (int i = 0; i < band.size(); i++) {
                position.put(band.get(i), (i + 0.5) / band.size());
            }
        }
        List<UUID> band = bands.get(b);
        Map<UUID, Double> barycenter = new HashMap<>();
        for (UUID id : band) {
            double sum = 0;
            int count = 0;
            for (UUID other : neighbours.get(id)) {
                int otherBand = bandIndex.get(other);
                if (fromAbove ? otherBand < b : otherBand > b) {
                    sum += position.get(other);
                    count++;
                }
            }
            barycenter.put(id, count == 0 ? position.get(id) : sum / count);
        }
        List<UUID> sorted = new ArrayList<>(band);
        Map<UUID, Integer> before = new HashMap<>();
        for (int i = 0; i < band.size(); i++) {
            before.put(band.get(i), i);
        }
        sorted.sort(Comparator.<UUID>comparingDouble(barycenter::get).thenComparingInt(before::get));
        bands.set(b, sorted);
    }

    // ── Размещение ──────────────────────────────────────────────────

    private Map<UUID, Bounds> place(List<List<UUID>> bands) {
        Spacing spacing = Spacing.of(strategy);
        int widest = roots.stream().mapToInt(this::width).max().orElse(DEFAULT_WIDTH);
        long area = roots.stream().mapToLong(id -> (long) (width(id) + spacing.column()) * (height(id) + spacing.row()))
                .sum();
        int limit = Math.max(widest, Math.max(spacing.minRowWidth(), (int) Math.round(Math.sqrt(area)
                * spacing.aspect())));

        List<List<List<UUID>>> rowsOfBands = new ArrayList<>();
        int overall = 0;
        for (List<UUID> band : bands) {
            List<List<UUID>> rows = wrap(band, limit, spacing);
            rowsOfBands.add(rows);
            for (List<UUID> row : rows) {
                overall = Math.max(overall, rowWidth(row, spacing));
            }
        }

        Map<UUID, Bounds> placed = new HashMap<>();
        int y = ORIGIN;
        for (List<List<UUID>> rows : rowsOfBands) {
            for (List<UUID> row : rows) {
                int x = ORIGIN + (spacing.centered() ? (overall - rowWidth(row, spacing)) / 2 : 0);
                int rowHeight = 0;
                for (UUID id : row) {
                    LayoutGraph.Node node = nodes.get(id);
                    placed.put(id, new Bounds(x, y, node.bounds().width(), node.bounds().height()));
                    x += width(id) + spacing.column();
                    rowHeight = Math.max(rowHeight, height(id));
                }
                y += rowHeight + spacing.row();
            }
            y += spacing.band() - spacing.row();
        }
        Map<UUID, Bounds> ordered = new LinkedHashMap<>();
        roots.forEach(id -> ordered.put(id, placed.get(id)));
        return ordered;
    }

    private List<List<UUID>> wrap(List<UUID> band, int limit, Spacing spacing) {
        List<List<UUID>> rows = new ArrayList<>();
        List<UUID> row = new ArrayList<>();
        int used = 0;
        for (UUID id : band) {
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

    private int rowWidth(List<UUID> row, Spacing spacing) {
        return row.stream().mapToInt(this::width).sum() + spacing.column() * (row.size() - 1);
    }

    private int width(UUID id) {
        int width = nodes.get(id).bounds().width();
        return width > 0 ? width : DEFAULT_WIDTH;
    }

    private int height(UUID id) {
        int height = nodes.get(id).bounds().height();
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
