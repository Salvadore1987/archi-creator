package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import static uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind.ELEMENT;
import static uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind.JUNCTION;
import static uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind.RELATIONSHIP;
import static uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind.VIEW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind.VIEW_EDGE;
import static uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind.VIEW_NODE;
import static uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.MetamodelPhase.PHASE_1;
import static uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.MetamodelPhase.PHASE_2;
import static uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.MetamodelPhase.PHASE_3;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Каталог типов ArchiMate 3.2 и реестр {@code xsi:type} ↔ внутренний тип.
 *
 * <p>Внутренний тип и {@code xsi:type} записываются одинаково —
 * {@code archimate:ApplicationComponent}, — поэтому реестр не переводит строки,
 * а отвечает, <em>что</em> за тип перед нами: элемент или связь, какого слоя,
 * с какой фазы редактируется. Тип вне каталога законен: он хранится и
 * выгружается как есть, его слой — {@link Layer#OTHER}.
 *
 * <p>Фазы: в фазе 1 Business, Application, Technology, все
 * связи и {@code Junction}; Motivation и Strategy — фаза 2; Physical и
 * Implementation &amp; Migration — фаза 3.
 */
public final class ArchiTypeRegistry {

    private static final ArchiTypeRegistry ARCHIMATE_32 = new ArchiTypeRegistry(catalog());

    private final Map<ArchiType, ConceptDefinition> concepts;

    private ArchiTypeRegistry(Map<ArchiType, ConceptDefinition> concepts) {
        this.concepts = Collections.unmodifiableMap(concepts);
    }

    public static ArchiTypeRegistry archimate32() {
        return ARCHIMATE_32;
    }

    public Optional<ConceptDefinition> find(ArchiType type) {
        return Optional.ofNullable(concepts.get(type));
    }

    /** Значение {@code xsi:type} из файла. Строка не в нотации Archi — не ошибка, а «не знаю». */
    public Optional<ConceptDefinition> findByXsiType(String xsiType) {
        try {
            return find(ArchiType.of(xsiType));
        } catch (IllegalArgumentException notArchiNotation) {
            return Optional.empty();
        }
    }

    public Layer layerOf(ArchiType type) {
        return find(type).map(ConceptDefinition::layer).orElse(Layer.OTHER);
    }

    /** Редактируется ли тип в текущей фазе. Незнакомый — нет, он opaque. */
    public boolean isSupported(ArchiType type) {
        return find(type).map(ConceptDefinition::supported).orElse(false);
    }

    /** Определения данного вида в порядке каталога. */
    public List<ConceptDefinition> concepts(ConceptKind kind) {
        return concepts.values().stream().filter(c -> c.kind() == kind).toList();
    }

    private static Map<ArchiType, ConceptDefinition> catalog() {
        var catalog = new Catalog();

        catalog.elements(Layer.BUSINESS, PHASE_1,
                "BusinessActor", "BusinessRole", "BusinessCollaboration", "BusinessInterface",
                "BusinessProcess", "BusinessFunction", "BusinessInteraction", "BusinessEvent",
                "BusinessService", "BusinessObject", "Contract", "Representation", "Product");
        catalog.elements(Layer.APPLICATION, PHASE_1,
                "ApplicationComponent", "ApplicationCollaboration", "ApplicationInterface",
                "ApplicationFunction", "ApplicationInteraction", "ApplicationProcess",
                "ApplicationEvent", "ApplicationService", "DataObject");
        catalog.elements(Layer.TECHNOLOGY, PHASE_1,
                "Node", "Device", "SystemSoftware", "TechnologyCollaboration", "TechnologyInterface",
                "Path", "CommunicationNetwork", "TechnologyFunction", "TechnologyProcess",
                "TechnologyInteraction", "TechnologyEvent", "TechnologyService", "Artifact");
        catalog.elements(Layer.PHYSICAL, PHASE_3,
                "Equipment", "Facility", "DistributionNetwork", "Material");
        catalog.elements(Layer.MOTIVATION, PHASE_2,
                "Stakeholder", "Driver", "Assessment", "Goal", "Outcome", "Principle",
                "Requirement", "Constraint", "Meaning", "Value");
        catalog.elements(Layer.STRATEGY, PHASE_2,
                "Resource", "Capability", "ValueStream", "CourseOfAction");
        catalog.elements(Layer.IMPLEMENTATION, PHASE_3,
                "WorkPackage", "Deliverable", "ImplementationEvent", "Plateau", "Gap");
        // Location и Grouping пока не отнесены ни к одной фазе метамодели.
        // До решения они opaque — хранятся и выгружаются, но не редактируются.
        catalog.add("Location", ELEMENT, Layer.OTHER, null);
        catalog.add("Grouping", ELEMENT, Layer.OTHER, null);
        catalog.add("Junction", JUNCTION, Layer.OTHER, PHASE_1);

        for (RelationshipType relationship : RelationshipType.values()) {
            catalog.add(relationship.archiType().simpleName(), RELATIONSHIP, Layer.OTHER, PHASE_1);
        }

        catalog.add("ArchimateDiagramModel", VIEW, Layer.OTHER, PHASE_1);
        catalog.add("DiagramObject", VIEW_NODE, Layer.OTHER, PHASE_1);
        catalog.add("Group", VIEW_NODE, Layer.OTHER, PHASE_1);
        catalog.add("Note", VIEW_NODE, Layer.OTHER, PHASE_1);
        catalog.add("Connection", VIEW_EDGE, Layer.OTHER, PHASE_1);
        return catalog.concepts;
    }

    private static final class Catalog {
        private final Map<ArchiType, ConceptDefinition> concepts = new LinkedHashMap<>();

        void elements(Layer layer, MetamodelPhase phase, String... names) {
            for (String name : names) {
                add(name, ELEMENT, layer, phase);
            }
        }

        void add(String name, ConceptKind kind, Layer layer, MetamodelPhase phase) {
            ArchiType type = ArchiType.ofSimpleName(name);
            ConceptDefinition previous = concepts.put(type,
                    new ConceptDefinition(type, kind, layer, Optional.ofNullable(phase)));
            if (previous != null) {
                throw new IllegalStateException("тип объявлен в каталоге дважды: " + type);
            }
        }
    }
}
