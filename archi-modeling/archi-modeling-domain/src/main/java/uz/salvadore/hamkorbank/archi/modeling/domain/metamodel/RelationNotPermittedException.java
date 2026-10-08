package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import java.util.Set;

/**
 * Попытка создать связь, которую матрица ArchiMate 3.2 для этой пары типов не
 * допускает (INV-MDL-007). Несёт перечень допустимых — ответ {@code 422} обязан
 * сказать не только «нельзя», но и «а что можно» (§8, UC-MDL-003).
 */
public final class RelationNotPermittedException extends RuntimeException {

    public static final String CODE = "RELATION_NOT_PERMITTED";
    public static final String INVARIANT = "INV-MDL-007";

    private final ArchiType source;
    private final ArchiType target;
    private final RelationshipType relationship;
    private final Set<RelationshipType> permitted;

    public RelationNotPermittedException(ArchiType source, ArchiType target, RelationshipType relationship,
                                         Set<RelationshipType> permitted) {
        super(INVARIANT + ": связь " + relationship + " от " + source + " к " + target
                + " не допускается матрицей ArchiMate 3.2; допустимы " + permitted);
        this.source = source;
        this.target = target;
        this.relationship = relationship;
        this.permitted = Set.copyOf(permitted);
    }

    public String code() {
        return CODE;
    }

    public ArchiType source() {
        return source;
    }

    public ArchiType target() {
        return target;
    }

    public RelationshipType relationship() {
        return relationship;
    }

    public Set<RelationshipType> permitted() {
        return permitted;
    }
}
