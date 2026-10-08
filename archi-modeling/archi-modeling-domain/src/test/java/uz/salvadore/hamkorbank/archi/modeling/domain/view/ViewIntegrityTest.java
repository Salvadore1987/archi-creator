package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;

class ViewIntegrityTest {

    private static final Bounds BOX = new Bounds(10, 10, 120, 55);

    @Test
    @DisplayName("INV-MDL-008: узел DIAGRAM_OBJECT ссылается на элемент своей модели")
    void diagramObjectRequiresElementOfSameModel() {
        ArchitectureModel model = Models.empty();
        ArchitectureModel other = Models.empty();
        Element foreign = Models.element(other, "BusinessActor", FolderType.BUSINESS, "Клиент");
        View view = newView(model);

        ModelingException rejected = assertThrows(ModelingException.class, () -> view.placeElement(model,
                foreign.id(), BOX, Optional.empty(), ViewNodeId.next(UUIDS), Models.archiId()));
        assertEquals("INV-MDL-008", rejected.code());

        assertThrows(IllegalArgumentException.class, () -> new ViewNode(ViewNodeId.next(UUIDS), Optional.empty(),
                Models.archiId(), DiagramType.DIAGRAM_OBJECT, Optional.empty(), BOX, StyleOverride.NONE,
                SortOrder.ofPosition(0), Optional.empty()), "DIAGRAM_OBJECT без элемента");
        assertThrows(IllegalArgumentException.class, () -> new ViewNode(ViewNodeId.next(UUIDS), Optional.empty(),
                Models.archiId(), DiagramType.GROUP, Optional.of(foreign.id()), BOX, StyleOverride.NONE,
                SortOrder.ofPosition(0), Optional.empty()), "у группы элемента нет");
    }

    @Test
    @DisplayName("UI-002: повторное размещение возвращает существующий узел")
    void placingPlacedElementReturnsExistingNode() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        View view = newView(model);

        ViewNode first = view.placeElement(model, component.id(), BOX, Optional.empty(), ViewNodeId.next(UUIDS),
                Models.archiId()).value();
        var second = view.placeElement(model, component.id(), new Bounds(300, 10, 120, 55), Optional.empty(),
                ViewNodeId.next(UUIDS), Models.archiId());

        assertFalse(second.created());
        assertEquals(first.id(), second.value().id());
        assertEquals(1, view.nodes().size());
    }

    @Test
    @DisplayName("INV-MDL-004: удаление элемента снимает его узлы, вложенные узлы и рёбра")
    void removingElementReferencesCascadesOnView() {
        ArchitectureModel model = Models.empty();
        Element gateway = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "Шлюз");
        Element antifraud = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "Антифрод");
        Relationship composition = Models.relate(model, "CompositionRelationship", gateway.id(), antifraud.id());
        View view = newView(model);
        ViewNode outer = view.placeElement(model, gateway.id(), new Bounds(0, 0, 300, 200), Optional.empty(),
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewNode inner = view.placeElement(model, antifraud.id(), BOX, Optional.of(outer.id()),
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
        ViewNode c = view.placeElement(model, component.id(), BOX, Optional.empty(), ViewNodeId.next(UUIDS),
                Models.archiId()).value();
        ViewNode a = view.placeElement(model, actor.id(), new Bounds(300, 10, 120, 55), Optional.empty(),
                ViewNodeId.next(UUIDS), Models.archiId()).value();

        assertThrows(ModelingException.class, () -> view.connect(model, serving.id(), a.id(), c.id(),
                ViewEdgeId.next(UUIDS), Models.archiId()), "ребро наоборот не изображает связь");

        ViewEdge edge = view.connect(model, serving.id(), c.id(), a.id(), ViewEdgeId.next(UUIDS),
                Models.archiId()).value();
        view.applyLayout(Map.of(a.id(), new Bounds(400, 40, 120, 55)),
                Map.of(edge.id(), List.of(new Bendpoint(0, 80, -100, 0))));
        assertEquals(400, view.requireNode(a.id()).bounds().x());
        assertEquals(1, view.requireEdge(edge.id()).bendpoints().size());
    }

    private static View newView(ArchitectureModel model) {
        return View.create(model.id(), model.placeNewView(ViewId.next(UUIDS), DiagramType.DIAGRAM_MODEL, "Контекст",
                Models.root(model, FolderType.DIAGRAMS), Models.archiId(), NOW));
    }
}
