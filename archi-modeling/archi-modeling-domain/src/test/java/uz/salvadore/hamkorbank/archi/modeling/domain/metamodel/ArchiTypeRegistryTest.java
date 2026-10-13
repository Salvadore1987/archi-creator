package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ArchiTypeRegistryTest {

    private final ArchiTypeRegistry registry = ArchiTypeRegistry.archimate32();

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "BusinessActor,        BUSINESS",
            "Product,              BUSINESS",
            "ApplicationComponent, APPLICATION",
            "DataObject,           APPLICATION",
            "Node,                 TECHNOLOGY",
            "Artifact,             TECHNOLOGY",
            "Equipment,            PHYSICAL",
            "Goal,                 MOTIVATION",
            "Capability,           STRATEGY",
            "WorkPackage,          IMPLEMENTATION",
            "Location,             OTHER",
            "Junction,             OTHER",
    })
    @DisplayName("слой выводится из типа")
    void layerIsDerivedFromType(String type, Layer expected) {
        assertEquals(expected, registry.layerOf(ArchiType.ofSimpleName(type)));
    }

    @Test
    @DisplayName("незнакомый тип: слой OTHER, не поддержан, но и не ошибка (FR-03)")
    void unknownTypeIsOtherAndOpaque() {
        ArchiType unknown = ArchiType.of("archimate:FutureConcept");

        assertEquals(Layer.OTHER, registry.layerOf(unknown));
        assertFalse(registry.isSupported(unknown));
        assertTrue(registry.find(unknown).isEmpty());
    }

    @Test
    @DisplayName("FR-07, FR-08: в фазах 1 и 2 редактируются Business, Application, Technology, Motivation и Strategy")
    void phasesOneAndTwoCoverFiveLayers() {
        Set<Layer> supportedLayers = registry.concepts(ConceptKind.ELEMENT).stream()
                .filter(ConceptDefinition::supported)
                .map(ConceptDefinition::layer)
                .collect(Collectors.toSet());

        assertEquals(Set.of(Layer.BUSINESS, Layer.APPLICATION, Layer.TECHNOLOGY, Layer.MOTIVATION, Layer.STRATEGY),
                supportedLayers);
    }

    @Test
    @DisplayName("FR-08: Capability и Goal редактируются — фаза 2 поддержана")
    void phaseTwoElementsAreSupported() {
        for (String type : List.of("Capability", "Resource", "ValueStream", "CourseOfAction", "Stakeholder",
                "Driver", "Assessment", "Goal", "Outcome", "Principle", "Requirement", "Constraint", "Meaning",
                "Value")) {
            assertTrue(registry.isSupported(ArchiType.ofSimpleName(type)), type);
        }
    }

    @Test
    @DisplayName("FR-08: фаза 3, Location и Grouping хранятся, но не редактируются")
    void outOfPhaseElementsAreKnownButOpaque() {
        for (String type : List.of("WorkPackage", "Plateau", "Equipment", "Facility", "Location", "Grouping")) {
            assertTrue(registry.find(ArchiType.ofSimpleName(type)).isPresent(), type);
            assertFalse(registry.isSupported(ArchiType.ofSimpleName(type)), type);
        }
    }

    @Test
    @DisplayName("FR-09: все одиннадцать связей и Junction поддержаны в фазе 1")
    void relationshipsAndJunctionAreSupported() {
        for (RelationshipType relationship : RelationshipType.values()) {
            assertEquals(ConceptKind.RELATIONSHIP, registry.find(relationship.archiType()).orElseThrow().kind());
            assertTrue(registry.isSupported(relationship.archiType()), relationship.name());
        }
        assertTrue(registry.isSupported(ArchiType.ofSimpleName("Junction")));
    }

    @Test
    @DisplayName("xsi:type из файла узнаётся, строка не в нотации Archi — просто не найдена")
    void lookupByXsiType() {
        assertEquals(ConceptKind.VIEW,
                registry.findByXsiType("archimate:ArchimateDiagramModel").orElseThrow().kind());
        assertEquals(ConceptKind.VIEW_NODE, registry.findByXsiType("archimate:Group").orElseThrow().kind());
        assertTrue(registry.findByXsiType("canvas:CanvasModel").isEmpty());
    }

    @Test
    @DisplayName("каталог элементов — 60 типов ArchiMate 3.2 плюс Junction")
    void catalogHasAllArchimateElements() {
        assertEquals(60, registry.concepts(ConceptKind.ELEMENT).size());
        assertEquals(1, registry.concepts(ConceptKind.JUNCTION).size());
    }
}
