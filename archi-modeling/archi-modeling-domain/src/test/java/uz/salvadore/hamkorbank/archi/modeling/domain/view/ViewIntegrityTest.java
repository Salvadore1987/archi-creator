package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;

class ViewIntegrityTest {

    private static final Bounds BOX = new Bounds(10, 10, 120, 55);

    @Test
    @DisplayName("INV-MDL-008: узел DIAGRAM_OBJECT ссылается на элемент своей модели")
    void diagramObjectRequiresElementOfSameModel() {
        ArchitectureModel model = Models.empty();
        ArchitectureModel other = Models.empty();
        Element foreign = Models.element(other, "BusinessActor", FolderType.BUSINESS, "Клиент");
        View view = newView(model);

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class, () -> view.placeElement(model,
                foreign.id(), BOX, null, ViewNodeId.next(UUIDS), Models.archiId()));
        assertEquals("INV-MDL-008", rejected.code());

        assertThrows(InvalidValueException.class, () -> new ViewNode(ViewNodeId.next(UUIDS), null,
                Models.archiId(), DiagramType.DIAGRAM_OBJECT, null, BOX, StyleOverride.NONE,
                SortOrder.ofPosition(0), null), "DIAGRAM_OBJECT без элемента");
        assertThrows(InvalidValueException.class, () -> new ViewNode(ViewNodeId.next(UUIDS), null,
                Models.archiId(), DiagramType.GROUP, foreign.id(), BOX, StyleOverride.NONE,
                SortOrder.ofPosition(0), null), "у группы элемента нет");
    }

    @Test
    @DisplayName("UI-002: повторное размещение возвращает существующий узел")
    void placingPlacedElementReturnsExistingNode() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        View view = newView(model);

        ViewNode first = view.placeElement(model, component.id(), BOX, null, ViewNodeId.next(UUIDS),
                Models.archiId()).value();
        ArchitectureModel.Created<ViewNode> second = view.placeElement(model, component.id(),
                new Bounds(300, 10, 120, 55), null,
                ViewNodeId.next(UUIDS), Models.archiId());

        assertFalse(second.created());
        assertEquals(first.id(), second.value().id());
        assertEquals(1, view.nodes().size());
    }

    @Test
    @DisplayName("дубль решает агрегат: идентификаторы нового объекта у существующего не запрашиваются")
    void duplicatesDoNotDrawNewIds() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");
        Relationship serving = Models.relate(model, "ServingRelationship", component.id(), actor.id());
        View view = newView(model);
        ViewNode c = view.placeElement(model, component.id(), BOX, null, ViewNodeId.next(UUIDS),
                Models.archiId()).value();
        ViewNode a = view.placeElement(model, actor.id(), new Bounds(300, 10, 120, 55), null,
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        view.connect(model, serving.id(), c.id(), a.id(), ViewEdgeId.next(UUIDS), Models.archiId());

        ArchitectureModel.Created<ViewNode> node = view.placeElement(model, component.id(), BOX, null,
                () -> fail("ключ узла не нужен"), () -> fail("archiId узла не нужен"));
        ArchitectureModel.Created<ViewEdge> edge = view.connect(model, serving.id(), c.id(), a.id(), List.of(),
                () -> fail("ключ ребра не нужен"), () -> fail("archiId ребра не нужен"));
        ArchitectureModel.Created<Relationship> relation = model.addRelationship(serving.archiType(),
                component.id(), actor.id(), null, null, null, null, () -> fail("ключ связи не нужен"),
                () -> fail("archiId связи не нужен"), NOW);

        assertFalse(node.created());
        assertFalse(edge.created());
        assertFalse(relation.created());
        assertEquals(serving.id(), relation.value().id());
    }

    @Test
    @DisplayName("INV-MDL-004: удаление элемента снимает его узлы, вложенные узлы и рёбра")
    void removingElementReferencesCascadesOnView() {
        ArchitectureModel model = Models.empty();
        Element gateway = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "Шлюз");
        Element antifraud = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "Антифрод");
        Relationship composition = Models.relate(model, "CompositionRelationship", gateway.id(), antifraud.id());
        View view = newView(model);
        ViewNode outer = view.placeElement(model, gateway.id(), new Bounds(0, 0, 300, 200), null,
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewNode inner = view.placeElement(model, antifraud.id(), BOX, outer.id(),
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewEdge edge = view.connect(model, composition.id(), outer.id(), inner.id(), ViewEdgeId.next(UUIDS),
                Models.archiId()).value();

        assertEquals(2000, edge.sortOrder().value(), "ребро встаёт в содержимое источника после вложенного узла");
        assertTrue(view.removeElementReferences(gateway.id()));

        assertEquals(0, view.nodes().size());
        assertEquals(0, view.edges().size());
    }

    @Test
    @DisplayName("INV-MDL-008: концы ребра изображают концы связи")
    void edgeEndsMustDepictRelationshipEnds() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");
        Relationship serving = Models.relate(model, "ServingRelationship", component.id(), actor.id());
        View view = newView(model);
        ViewNode c = view.placeElement(model, component.id(), BOX, null, ViewNodeId.next(UUIDS),
                Models.archiId()).value();
        ViewNode a = view.placeElement(model, actor.id(), new Bounds(300, 10, 120, 55), null,
                ViewNodeId.next(UUIDS), Models.archiId()).value();

        assertThrows(ModelingDomainException.class, () -> view.connect(model, serving.id(), a.id(), c.id(),
                ViewEdgeId.next(UUIDS), Models.archiId()), "ребро наоборот не изображает связь");

        ViewEdge edge = view.connect(model, serving.id(), c.id(), a.id(), ViewEdgeId.next(UUIDS),
                Models.archiId()).value();
        view.applyLayout(Map.of(a.id(), new Bounds(400, 40, 120, 55)),
                Map.of(edge.id(), List.of(new Bendpoint(0, 80, -100, 0))));
        assertEquals(400, view.requireNode(a.id()).bounds().x());
        assertEquals(1, view.requireEdge(edge.id()).bendpoints().size());
    }

    @Test
    @DisplayName("INV-MDL-001: узел импортированного представления не берёт archi_id самого представления")
    void importedNodeCannotReuseViewArchiId() {
        ArchitectureModel model = Models.empty();
        View view = newView(model);
        ViewNode group = new ViewNode(ViewNodeId.next(UUIDS), null, view.archiId(), DiagramType.GROUP,
                null, BOX, StyleOverride.NONE, SortOrder.ofPosition(0), null);

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> View.imported(view.header(), List.of(group), List.of(), model));
        assertEquals("INV-MDL-001", rejected.code());
    }

    @Test
    @DisplayName("INV-MDL-008: представление импортируется только в свою модель")
    void importedViewOfOtherModelIsRejected() {
        ArchitectureModel model = Models.empty();
        View view = newView(model);

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> View.imported(view.header(), List.of(), List.of(), Models.empty()));

        assertEquals(ModelingCodes.VIEW_REFERENCE, rejected.code());
        assertEquals(ModelingMessages.VIEW_OF_OTHER_MODEL, rejected.reason().key());
    }

    @Test
    @DisplayName("INV-MDL-008: узел импортированного представления изображает элемент своей модели")
    void importedNodeOfForeignElementIsRejected() {
        ArchitectureModel model = Models.empty();
        Element foreign = Models.element(Models.empty(), "BusinessActor", FolderType.BUSINESS, "Клиент");
        ViewNode node = new ViewNode(ViewNodeId.next(UUIDS), null, Models.archiId(), DiagramType.DIAGRAM_OBJECT,
                foreign.id(), BOX, StyleOverride.NONE, SortOrder.ofPosition(0), null);

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> View.imported(newView(model).header(), List.of(node), List.of(), model));

        assertEquals(ModelingCodes.VIEW_REFERENCE, rejected.code());
        assertEquals(ModelingMessages.NODE_ELEMENT_ELSEWHERE, rejected.reason().key());
    }

    @Test
    @DisplayName("INV-MDL-008: родитель узла импортированного представления лежит на том же представлении")
    void importedNodeWithParentElsewhereIsRejected() {
        ArchitectureModel model = Models.empty();
        ViewNode orphan = group(ViewNodeId.next(UUIDS), ViewNodeId.next(UUIDS));

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> View.imported(newView(model).header(), List.of(orphan), List.of(), model));

        assertEquals(ModelingCodes.VIEW_REFERENCE, rejected.code());
        assertEquals(ModelingMessages.NODE_PARENT_ELSEWHERE, rejected.reason().key());
    }

    @Test
    @DisplayName("INV-MDL-008: узлы импортированного представления не вложены друг в друга по кругу")
    void importedNestingCycleIsRejected() {
        ArchitectureModel model = Models.empty();
        ViewNodeId first = ViewNodeId.next(UUIDS);
        ViewNodeId second = ViewNodeId.next(UUIDS);

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> View.imported(newView(model).header(), List.of(group(first, second), group(second, first)),
                        List.of(), model));

        assertEquals(ModelingCodes.VIEW_REFERENCE, rejected.code());
        assertEquals(ModelingMessages.NODE_NESTING_CYCLE, rejected.reason().key());
    }

    @Test
    @DisplayName("INV-MDL-001: ребро импортированного представления не берёт archi_id узла")
    void importedEdgeCannotReuseNodeArchiId() {
        ArchitectureModel model = Models.empty();
        ViewNode source = group(ViewNodeId.next(UUIDS), null);
        ViewNode target = group(ViewNodeId.next(UUIDS), null);
        ViewEdge edge = edge(source.archiId(), null, source.id(), target.id());

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> View.imported(newView(model).header(), List.of(source, target), List.of(edge), model));

        assertEquals(ModelingCodes.ARCHI_ID_UNIQUE, rejected.code());
        assertEquals(ModelingMessages.ARCHI_ID_REPEATED_ON_VIEW, rejected.reason().key());
    }

    @Test
    @DisplayName("INV-MDL-008: концы ребра импортированного представления лежат на том же представлении")
    void importedEdgeWithEndElsewhereIsRejected() {
        ArchitectureModel model = Models.empty();
        ViewNode target = group(ViewNodeId.next(UUIDS), null);
        ViewEdge edge = edge(Models.archiId(), null, ViewNodeId.next(UUIDS), target.id());

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> View.imported(newView(model).header(), List.of(target), List.of(edge), model));

        assertEquals(ModelingCodes.VIEW_REFERENCE, rejected.code());
        assertEquals(ModelingMessages.EDGE_END_ELSEWHERE, rejected.reason().key());
    }

    @Test
    @DisplayName("INV-MDL-008: ребро импортированного представления изображает связь своей модели")
    void importedEdgeOfForeignRelationshipIsRejected() {
        ArchitectureModel model = Models.empty();
        ViewNode source = group(ViewNodeId.next(UUIDS), null);
        ViewNode target = group(ViewNodeId.next(UUIDS), null);
        ViewEdge edge = edge(Models.archiId(), RelationshipId.next(UUIDS), source.id(), target.id());

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> View.imported(newView(model).header(), List.of(source, target), List.of(edge), model));

        assertEquals(ModelingCodes.VIEW_REFERENCE, rejected.code());
        assertEquals(ModelingMessages.EDGE_RELATIONSHIP_ELSEWHERE, rejected.reason().key());
    }

    private static ViewNode group(ViewNodeId id, ViewNodeId parentId) {
        return new ViewNode(id, parentId, Models.archiId(), DiagramType.GROUP, null, BOX, StyleOverride.NONE,
                SortOrder.ofPosition(0), null);
    }

    private static ViewEdge edge(ArchiId archiId, RelationshipId relationshipId, ViewEndpoint source,
                                 ViewEndpoint target) {
        return new ViewEdge(ViewEdgeId.next(UUIDS), archiId, DiagramType.CONNECTION, relationshipId, source, target,
                List.of(), StyleOverride.NONE, SortOrder.ofPosition(0), null);
    }

    private static View newView(ArchitectureModel model) {
        return View.create(model.id(), model.placeNewView(ViewId.next(UUIDS), DiagramType.DIAGRAM_MODEL, "Контекст",
                Models.root(model, FolderType.DIAGRAMS), Models.archiId(), NOW));
    }
}
