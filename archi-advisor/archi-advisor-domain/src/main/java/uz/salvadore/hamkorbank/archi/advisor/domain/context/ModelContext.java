package uz.salvadore.hamkorbank.archi.advisor.domain.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;

/**
 * Усечённое представление модели под одну функцию помощника. Неизменяемо, живёт одну
 * сессию.
 *
 * <p>Флаг {@code truncated} обязателен: ревью по части модели, поданное как ревью
 * модели, хуже отсутствия ревью, и ответ обязан оговорить усечение.
 *
 * @param scopeSize сколько элементов было в области до усечения — от него считается
 *                  доля покрытия
 */
public record ModelContext(AdvisorFunction function, List<ContextElement> elements,
                           List<ContextRelation> relationships, List<ContextView> views, int tokenEstimate,
                           boolean truncated, int scopeSize) {

    /** Служебные символы компактного JSON на запись: кавычки, имена полей, разделители. */
    private static final int ELEMENT_OVERHEAD = 48;
    private static final int RELATION_OVERHEAD = 48;
    private static final int VIEW_OVERHEAD = 32;
    private static final int ID_OVERHEAD = 3;
    private static final int CHARS_PER_TOKEN = 4;

    public ModelContext {
        Objects.requireNonNull(function, "function");
        elements = List.copyOf(elements);
        relationships = List.copyOf(relationships);
        views = List.copyOf(views);
        if (tokenEstimate < 0 || scopeSize < 0) {
            throw new InvalidValueException(AdvisorMessages.USAGE_NEGATIVE);
        }
    }

    /** Полный контекст области — без усечения. */
    public static ModelContext of(AdvisorFunction function, List<ContextElement> elements,
                                  List<ContextRelation> relationships, List<ContextView> views) {
        return new ModelContext(function, elements, relationships, views, estimate(elements, relationships, views),
                false, elements.size());
    }

    /**
     * Контекст в пределах {@code maxTokens}. Сначала уходит документация, затем элементы
     * — по слоям снизу вверх, внутри слоя первыми уходят наименее связанные. Связь
     * остаётся, только если остались оба её конца; представление — со списком только
     * оставшихся элементов.
     */
    public ModelContext fit(int maxTokens) {
        if (tokenEstimate <= maxTokens) {
            return this;
        }
        List<ContextElement> bare = elements.stream().map(ContextElement::withoutDocumentation).toList();
        if (estimate(bare, relationships, views) <= maxTokens) {
            return new ModelContext(function, bare, relationships, views, estimate(bare, relationships, views), true,
                    scopeSize);
        }
        List<ContextElement> ranked = ranked(bare);
        int low = 0;
        int high = ranked.size();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (prefix(ranked, middle).tokenEstimate <= maxTokens) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        ModelContext kept = prefix(ranked, low);
        return new ModelContext(function, kept.elements, kept.relationships, kept.views, kept.tokenEstimate, true,
                scopeSize);
    }

    /** Доля элементов области, оставшихся после усечения: 1 — ничего не потеряно. */
    public double coverage() {
        return scopeSize == 0 ? 1.0 : (double) elements.size() / scopeSize;
    }

    /** Есть ли в контексте объект с таким {@code archiId} — элемент, связь или представление. */
    public boolean contains(String archiId) {
        return elements.stream().anyMatch(e -> e.archiId().equals(archiId))
                || relationships.stream().anyMatch(r -> r.archiId().equals(archiId))
                || views.stream().anyMatch(v -> v.archiId().equals(archiId));
    }

    /** Отображаемое имя объекта контекста; у связи без имени — её тип. */
    public Optional<String> nameOf(String archiId) {
        return elements.stream().filter(e -> e.archiId().equals(archiId)).map(ContextElement::name).findFirst()
                .or(() -> views.stream().filter(v -> v.archiId().equals(archiId)).map(ContextView::name).findFirst())
                .or(() -> relationships.stream().filter(r -> r.archiId().equals(archiId))
                        .map(ContextRelation::archiType).findFirst());
    }

    private List<ContextElement> ranked(List<ContextElement> source) {
        Map<String, Integer> degree = new HashMap<>();
        for (ContextRelation r : relationships) {
            degree.merge(r.sourceArchiId(), 1, Integer::sum);
            degree.merge(r.targetArchiId(), 1, Integer::sum);
        }
        Map<String, Integer> position = new HashMap<>();
        for (int i = 0; i < source.size(); i++) {
            position.put(source.get(i).archiId(), i);
        }
        List<ContextElement> ranked = new ArrayList<>(source);
        ranked.sort(Comparator.comparing(ContextElement::layer)
                .thenComparing(e -> -degree.getOrDefault(e.archiId(), 0))
                .thenComparing(e -> position.get(e.archiId())));
        return ranked;
    }

    private ModelContext prefix(List<ContextElement> ranked, int count) {
        Set<String> kept = new HashSet<>();
        ranked.subList(0, count).forEach(e -> kept.add(e.archiId()));
        List<ContextElement> keptElements = elements.stream().filter(e -> kept.contains(e.archiId()))
                .map(ContextElement::withoutDocumentation).toList();
        List<ContextRelation> keptRelations = relationships.stream()
                .filter(r -> kept.contains(r.sourceArchiId()) && kept.contains(r.targetArchiId())).toList();
        List<ContextView> keptViews = views.stream()
                .map(v -> new ContextView(v.archiId(), v.name(),
                        v.elementArchiIds().stream().filter(kept::contains).toList()))
                .toList();
        return new ModelContext(function, keptElements, keptRelations, keptViews,
                estimate(keptElements, keptRelations, keptViews), true, scopeSize);
    }

    static int estimate(List<ContextElement> elements, List<ContextRelation> relationships, List<ContextView> views) {
        long chars = 0;
        for (ContextElement e : elements) {
            chars += ELEMENT_OVERHEAD + e.archiId().length() + e.archiType().length() + e.name().length()
                    + (e.documentation() == null ? 0 : e.documentation().length());
        }
        for (ContextRelation r : relationships) {
            chars += RELATION_OVERHEAD + r.archiId().length() + r.archiType().length() + r.sourceArchiId().length()
                    + r.targetArchiId().length();
        }
        for (ContextView v : views) {
            chars += VIEW_OVERHEAD + v.archiId().length() + v.name().length();
            for (String id : v.elementArchiIds()) {
                chars += ID_OVERHEAD + id.length();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, chars / CHARS_PER_TOKEN);
    }
}
