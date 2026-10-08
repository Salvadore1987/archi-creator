package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NestingRulesTest {

    private final NestingRules rules = NestingRules.archimate32();
    private final RelationMatrix matrix = RelationMatrix.archimate32();

    @ParameterizedTest(name = "{1} внутри {0} → {2}")
    @CsvSource({
            "ApplicationComponent, ApplicationComponent, COMPOSITION",
            "ApplicationComponent, ApplicationInterface, COMPOSITION",
            "ApplicationComponent, ApplicationFunction,  ASSIGNMENT",
            "BusinessActor,        BusinessRole,         ASSIGNMENT",
            "BusinessRole,         BusinessProcess,      ASSIGNMENT",
            "Node,                 SystemSoftware,       COMPOSITION",
            "Node,                 Artifact,             ASSIGNMENT",
            "BusinessProcess,      BusinessProcess,      COMPOSITION",
    })
    @DisplayName("FR-14: вложенность подразумевает composition либо assignment по паре")
    void nestingImpliesRelationshipByPair(String parent, String child, RelationshipType expected) {
        assertEquals(Optional.of(expected),
                rules.impliedRelationship(ArchiType.ofSimpleName(parent), ArchiType.ofSimpleName(child)));
    }

    @Test
    @DisplayName("пара без composition и assignment в матрице связи не подразумевает")
    void pairWithoutStructuralRelationImpliesNothing() {
        assertTrue(rules.impliedRelationship(
                ArchiType.ofSimpleName("DataObject"), ArchiType.ofSimpleName("ApplicationComponent")).isEmpty());
    }

    @Test
    @DisplayName("на каждую пару типов фазы 1 подразумеваемая связь разрешена матрицей")
    void impliedRelationshipIsAlwaysPermitted() {
        List<ArchiType> types = ArchiTypeRegistry.archimate32().concepts(ConceptKind.ELEMENT).stream()
                .filter(ConceptDefinition::supported)
                .map(ConceptDefinition::type)
                .toList();
        for (ArchiType parent : types) {
            for (ArchiType child : types) {
                rules.impliedRelationship(parent, child).ifPresent(relationship ->
                        assertTrue(matrix.isPermitted(parent, child, relationship), parent + " ⊃ " + child));
                boolean structural = matrix.isPermitted(parent, child, RelationshipType.COMPOSITION)
                        || matrix.isPermitted(parent, child, RelationshipType.ASSIGNMENT);
                assertEquals(structural, rules.impliedRelationship(parent, child).isPresent(), parent + " ⊃ " + child);
            }
        }
    }
}
