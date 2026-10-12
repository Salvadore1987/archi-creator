package uz.salvadore.hamkorbank.archi.advisor.domain.context;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;

/**
 * Снимок модели, каким его отдаёт modeling через порт чтения: элементы, связи
 * и представления без геометрии. Из него выбирается область под функцию —
 * вся модель, одно представление, окрестность элемента, каталог имён.
 */
public record ModelGraph(List<ContextElement> elements, List<ContextRelation> relationships, List<GraphView> views) {

    public ModelGraph {
        elements = List.copyOf(elements);
        relationships = List.copyOf(relationships);
        views = List.copyOf(views);
    }

    /** Вся модель — для ревью и документации по модели. */
    public ModelContext whole(AdvisorFunction function) {
        return ModelContext.of(function, elements, relationships, views.stream().map(GraphView::toContext).toList());
    }

    /** Одно представление: его элементы и связи между ними. Пусто — такого представления нет. */
    public Optional<ModelContext> ofView(AdvisorFunction function, ViewId viewId) {
        return views.stream().filter(v -> v.id().equals(viewId)).findFirst().map(view -> {
            Set<String> placed = new HashSet<>(view.elementArchiIds());
            return ModelContext.of(function, elements.stream().filter(e -> placed.contains(e.archiId())).toList(),
                    relationships.stream()
                            .filter(r -> placed.contains(r.sourceArchiId()) && placed.contains(r.targetArchiId()))
                            .toList(),
                    List.of(view.toContext()));
        });
    }

    /**
     * Элемент и соседи на {@code hops} шагов по связям в обе стороны — для подсказки
     * и анализа влияния. Пусто — элемента нет.
     */
    public Optional<ModelContext> around(AdvisorFunction function, String archiId, int hops) {
        if (element(archiId).isEmpty()) {
            return Optional.empty();
        }
        Map<String, Set<String>> adjacent = new HashMap<>();
        for (ContextRelation r : relationships) {
            adjacent.computeIfAbsent(r.sourceArchiId(), k -> new HashSet<>()).add(r.targetArchiId());
            adjacent.computeIfAbsent(r.targetArchiId(), k -> new HashSet<>()).add(r.sourceArchiId());
        }
        Set<String> reached = new HashSet<>(Set.of(archiId));
        Deque<String> frontier = new ArrayDeque<>(List.of(archiId));
        for (int step = 0; step < hops && !frontier.isEmpty(); step++) {
            Deque<String> next = new ArrayDeque<>();
            for (String id : frontier) {
                for (String neighbour : adjacent.getOrDefault(id, Set.of())) {
                    if (reached.add(neighbour)) {
                        next.add(neighbour);
                    }
                }
            }
            frontier = next;
        }
        List<ContextElement> scoped = elements.stream().filter(e -> reached.contains(e.archiId())).toList();
        List<ContextRelation> scopedRelations = relationships.stream()
                .filter(r -> reached.contains(r.sourceArchiId()) && reached.contains(r.targetArchiId())).toList();
        List<ContextView> scopedViews = views.stream().filter(v -> v.elementArchiIds().contains(archiId))
                .map(v -> new ContextView(v.archiId(), v.name(),
                        v.elementArchiIds().stream().filter(reached::contains).toList()))
                .toList();
        return Optional.of(ModelContext.of(function, scoped, scopedRelations, scopedViews));
    }

    /** Каталог существующих элементов — имена и типы без связей: плану нужно, что уже есть. */
    public ModelContext catalog(AdvisorFunction function) {
        return ModelContext.of(function, elements.stream().map(ContextElement::withoutDocumentation).toList(),
                List.of(), List.of());
    }

    public Optional<ContextElement> element(String archiId) {
        return elements.stream().filter(e -> e.archiId().equals(archiId)).findFirst();
    }

    public Optional<GraphView> view(ViewId viewId) {
        return views.stream().filter(v -> v.id().equals(viewId)).findFirst();
    }
}
