package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.util.List;

/**
 * Описание предлагаемой структуры — не команда. Элементы названы по имени и типу,
 * применить план нечем: палитра лишь фильтруется по его типам, а собирает схему
 * архитектор.
 *
 * @param notes пояснение к плану; {@code null} — нет, пустое приводится к {@code null}
 */
public record ModelPlan(List<PlannedElement> elements, List<PlannedRelation> relationships, String notes) {

    public ModelPlan {
        elements = List.copyOf(elements);
        relationships = List.copyOf(relationships);
        if (notes != null && notes.isBlank()) {
            notes = null;
        }
    }

    /** Типы элементов плана без повторов, в порядке появления — по ним фильтруется палитра. */
    public List<String> elementTypes() {
        return elements.stream().map(PlannedElement::archiType).distinct().toList();
    }
}
