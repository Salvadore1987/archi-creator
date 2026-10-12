package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import java.util.Set;

/**
 * Нарушение матрицы в уже существующей связи — например, пришедшей из импорта.
 * Не отказ, а запись отчёта валидации: что нарушено и что было бы допустимо.
 */
public record RelationViolation(ArchiType source, ArchiType target, RelationshipType relationship,
                                Set<RelationshipType> permitted) {

    public static final String CODE = RelationNotPermittedException.CODE;

    public RelationViolation {
        permitted = Set.copyOf(permitted);
    }
}
