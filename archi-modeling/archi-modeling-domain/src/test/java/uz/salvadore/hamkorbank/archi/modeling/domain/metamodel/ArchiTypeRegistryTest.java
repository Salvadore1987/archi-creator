package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @DisplayName("FR-07: в фазе 1 редактируются ровно Business, Application и Technology")
    void phaseOneCoversThreeLayers() {
        Set<Layer> supportedLayers = registry.concepts(ConceptKind.ELEMENT).stream()
                .filter(ConceptDefinition::supported)
                .map(ConceptDefinition::layer)
                .collect(Collectors.toSet());

        assertEquals(Set.of(Layer.BUSINESS, Layer.APPLICATION, Layer.TECHNOLOGY), supportedLayers);
    }

    @Test
    @DisplayName("FR-08: Capability и Location хранятся, но в фазе 1 не редактируются")
    void outOfPhaseElementsAreKnownButOpaque() {
        assertTrue(registry.find(ArchiType.ofSimpleName("Capability")).isPresent());
        assertFalse(registry.isSupported(ArchiType.ofSimpleName("Capability")));
        assertFalse(registry.isSupported(ArchiType.ofSimpleName("Location")));
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
