package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import java.util.Arrays;
import java.util.Optional;

/**
 * Одиннадцать типов связей ArchiMate 3.2.
 *
 * <p>Здесь enum уместен, в отличие от {@link ArchiType}: список связей закрыт
 * спецификацией языка, а связь неизвестного типа в файле не теряется —
 * её {@code archiType} хранится строкой, просто метамодель о ней не судит.
 *
 * <p>{@code key} — буква из таблицы связей Archi ({@code relationships-keys.xml}),
 * которой записана матрица {@link RelationMatrix}.
 */
public enum RelationshipType {

    COMPOSITION('c', "Composition"),
    AGGREGATION('g', "Aggregation"),
    ASSIGNMENT('i', "Assignment"),
    REALIZATION('r', "Realization"),
    SERVING('v', "Serving"),
    ACCESS('a', "Access"),
    INFLUENCE('n', "Influence"),
    TRIGGERING('t', "Triggering"),
    FLOW('f', "Flow"),
    SPECIALIZATION('s', "Specialization"),
    ASSOCIATION('o', "Association");

    private final char key;
    private final ArchiType archiType;

    RelationshipType(char key, String name) {
        this.key = key;
        this.archiType = ArchiType.ofSimpleName(name + "Relationship");
    }

    public ArchiType archiType() {
        return archiType;
    }

    char key() {
        return key;
    }

    public static Optional<RelationshipType> fromArchiType(ArchiType type) {
        return Arrays.stream(values()).filter(r -> r.archiType.equals(type)).findFirst();
    }

    static Optional<RelationshipType> fromKey(char key) {
        return Arrays.stream(values()).filter(r -> r.key == key).findFirst();
    }
}
