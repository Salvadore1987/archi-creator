package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import java.util.List;
import java.util.Optional;

/**
 * Какая связь подразумевается, когда узел помещают внутрь другого (FR-14, UI-004).
 *
 * <p>Спека допускает две — composition либо assignment, «по типу пары».
 * Правило: composition, если матрица её разрешает; иначе assignment, если
 * разрешает она; иначе вложенность связи не подразумевает, и решать,
 * что нарисовано, остаётся архитектору. Composition первой — потому что
 * там, где допустимы обе (Node и SystemSoftware), вложенность на холсте
 * читается как «состоит из», а не «назначен на».
 *
 * <p>Живёт в метамодели, а не в интерфейсе: правило проверяет та же матрица,
 * что и создание связи, и UI-004 не должен уметь создать то, что отвергнет
 * {@link RelationMatrix#requirePermitted}.
 */
public final class NestingRules {

    private static final List<RelationshipType> PREFERENCE =
            List.of(RelationshipType.COMPOSITION, RelationshipType.ASSIGNMENT);

    private static final NestingRules ARCHIMATE_32 = new NestingRules(RelationMatrix.archimate32());

    private final RelationMatrix matrix;

    NestingRules(RelationMatrix matrix) {
        this.matrix = matrix;
    }

    public static NestingRules archimate32() {
        return ARCHIMATE_32;
    }

    /** Связь от родителя к вложенному; пусто — вложенность связи не подразумевает. */
    public Optional<RelationshipType> impliedRelationship(ArchiType parent, ArchiType child) {
        return PREFERENCE.stream().filter(r -> matrix.isPermitted(parent, child, r)).findFirst();
    }
}
