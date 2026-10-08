package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class RelationMatrixTest {

    private static final ArchiType ACTOR = ArchiType.ofSimpleName("BusinessActor");
    private static final ArchiType COMPONENT = ArchiType.ofSimpleName("ApplicationComponent");

    private final RelationMatrix matrix = RelationMatrix.archimate32();
    private final ArchiTypeRegistry registry = ArchiTypeRegistry.archimate32();

    @Test
    @DisplayName("INV-MDL-007: недопустимая связь отклоняется при создании, с перечнем допустимых")
    void forbiddenRelationIsRejectedOnCreate() {
        RelationNotPermittedException e = assertThrows(RelationNotPermittedException.class,
                () -> matrix.requirePermitted(ACTOR, COMPONENT, RelationshipType.COMPOSITION));

        assertEquals("RELATION_NOT_PERMITTED", e.code());
        assertEquals(EnumSet.of(RelationshipType.FLOW, RelationshipType.ASSOCIATION,
                RelationshipType.TRIGGERING, RelationshipType.SERVING), e.permitted());
    }

    @Test
    @DisplayName("INV-MDL-007: нарушение из импорта сообщается, а не отклоняется (FR-10)")
    void importedViolationIsReportedNotRejected() {
        RelationViolation violation = assertDoesNotThrow(() ->
                matrix.check(ACTOR, COMPONENT, RelationshipType.COMPOSITION.archiType())).orElseThrow();

        assertEquals(RelationshipType.COMPOSITION, violation.relationship());
        assertEquals("RELATION_NOT_PERMITTED", RelationViolation.CODE);
    }

    @Test
    @DisplayName("допустимая связь проходит и при создании, и при импорте")
    void permittedRelationPasses() {
        ArchiType role = ArchiType.ofSimpleName("BusinessRole");

        assertDoesNotThrow(() -> matrix.requirePermitted(ACTOR, role, RelationshipType.ASSIGNMENT));
        assertTrue(matrix.check(ACTOR, role, RelationshipType.ASSIGNMENT.archiType()).isEmpty());
    }

    @Test
    @DisplayName("FR-03: о связи между незнакомыми типами матрица не судит")
    void unknownTypesProduceNoViolation() {
        ArchiType future = ArchiType.of("archimate:FutureConcept");

        assertTrue(matrix.check(future, COMPONENT, RelationshipType.COMPOSITION.archiType()).isEmpty());
        assertTrue(matrix.check(ACTOR, COMPONENT, ArchiType.of("archimate:FutureRelationship")).isEmpty());
        assertTrue(matrix.permitted(future, COMPONENT).isEmpty());
    }

    @Test
    @DisplayName("связь может быть концом ассоциации, но не композиции")
    void relationshipAsEndpoint() {
        ArchiType serving = RelationshipType.SERVING.archiType();

        assertTrue(matrix.isPermitted(COMPONENT, serving, RelationshipType.ASSOCIATION));
        assertTrue(matrix.check(COMPONENT, serving, RelationshipType.COMPOSITION.archiType()).isPresent());
    }

    @ParameterizedTest(name = "{0} —{2}→ {1}: {3}")
    @CsvFileSource(resources = "relation-matrix-cases.csv", numLinesToSkip = 1)
    @DisplayName("§9.2: тройки из спецификации ArchiMate 3.2")
    void matrixAgreesWithSpecification(String source, String target, RelationshipType relationship,
                                       boolean expected) {
        assertEquals(expected, matrix.isPermitted(
                ArchiType.ofSimpleName(source), ArchiType.ofSimpleName(target), relationship));
    }

    @Test
    @DisplayName("ассоциация допустима между любыми двумя элементами")
    void associationIsAlwaysPermittedBetweenElements() {
        for (ArchiType source : elementTypes()) {
            for (ArchiType target : elementTypes()) {
                assertTrue(matrix.isPermitted(source, target, RelationshipType.ASSOCIATION), source + " → " + target);
            }
        }
    }

    @Test
    @DisplayName("специализация — внутри одного типа и двух пар-подтипов языка (кроме Grouping и Junction)")
    void specializationRequiresSameType() {
        // ArchiMate 3.2 сам определяет Contract как специализацию BusinessObject,
        // а Constraint — как специализацию Requirement.
        Set<Set<String>> subtypePairs = Set.of(Set.of("BusinessObject", "Contract"), Set.of("Requirement", "Constraint"));
        List<ArchiType> types = elementTypes().stream()
                .filter(t -> !t.simpleName().equals("Grouping") && !t.simpleName().equals("Junction"))
                .toList();
        for (ArchiType source : types) {
            for (ArchiType target : types) {
                boolean expected = source.equals(target)
                        || subtypePairs.contains(Set.of(source.simpleName(), target.simpleName()));
                assertEquals(expected,
                        matrix.isPermitted(source, target, RelationshipType.SPECIALIZATION), source + " → " + target);
            }
        }
    }

    @Test
    @DisplayName("каталог и матрица знают один и тот же набор элементов")
    void catalogAndMatrixAgree() {
        for (ArchiType type : elementTypes()) {
            assertTrue(matrix.knows(type), "матрица не знает " + type);
        }
    }

    private List<ArchiType> elementTypes() {
        return java.util.stream.Stream.concat(
                        registry.concepts(ConceptKind.ELEMENT).stream(),
                        registry.concepts(ConceptKind.JUNCTION).stream())
                .map(ConceptDefinition::type)
                .toList();
    }
}
