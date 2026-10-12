package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;

class ViewEditingTest {

    private static final Bounds BOX = new Bounds(10, 10, 120, 55);

    private final ArchitectureModel model = Models.empty();
    private final View view = View.create(model.id(), model.placeNewView(ViewId.next(UUIDS),
            DiagramType.DIAGRAM_MODEL, "Контекст", Models.root(model, FolderType.DIAGRAMS), Models.archiId(), NOW));

    @Test
    @DisplayName("UC-MDL-008: вложенный узел встаёт в конец содержимого контейнера, цикл вложенности — отказ")
    void nodeMovesIntoContainerAtEndOfContent() {
        Element node = Models.element(model, "Node", FolderType.TECHNOLOGY, "Сервер");
        Element software = Models.element(model, "SystemSoftware", FolderType.TECHNOLOGY, "СУБД");
        Element device = Models.element(model, "Device", FolderType.TECHNOLOGY, "Стойка");
        ViewNode server = view.placeElement(model, node.id(), new Bounds(0, 0, 400, 300), Optional.empty(),
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewNode inside = view.placeElement(model, device.id(), BOX, Optional.of(server.id()),
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewNode dbms = view.placeElement(model, software.id(), new Bounds(500, 40, 120, 55), Optional.empty(),
                ViewNodeId.next(UUIDS), Models.archiId()).value();

        ViewNode moved = view.moveToParent(dbms.id(), Optional.of(server.id()), 200, 40);

        assertEquals(Optional.of(server.id()), moved.parentId());
        assertEquals(new Bounds(200, 40, 120, 55), moved.bounds(), "координаты — относительно контейнера");
        assertTrue(moved.sortOrder().value() > inside.sortOrder().value(), "в конце содержимого контейнера");
        assertEquals(inside.sortOrder(), view.requireNode(inside.id()).sortOrder(), "соседи не перенумерованы");

        ViewNode back = view.moveToParent(dbms.id(), Optional.empty(), 700, 40);
        assertEquals(Optional.empty(), back.parentId());
        assertEquals("INV-MDL-008", assertThrows(ModelingException.class,
                () -> view.moveToParent(server.id(), Optional.of(inside.id()), 0, 0)).code(), "контейнер внутри себя");
        assertEquals("INV-MDL-008", assertThrows(ModelingException.class,
                () -> view.moveToParent(server.id(), Optional.of(server.id()), 0, 0)).code());

        ViewNode same = view.moveToParent(inside.id(), Optional.of(server.id()), 30, 30);
        assertEquals(inside.sortOrder(), same.sortOrder(), "в том же родителе место не меняется");
    }

    @Test
    @DisplayName("UC-MDL-008: группа и заметка — узлы без элемента, подпись у группы, текст у заметки")
    void groupAndNoteHaveNoElement() {
        ViewNode group = view.placeAnnotation(ViewNodeKind.GROUP, Optional.of("Ядро"), Optional.empty(),
                Bounds.ofNew(ViewNodeKind.GROUP, 0, 0, Optional.empty(), Optional.empty()), Optional.empty(),
                ViewNodeId.next(UUIDS), Models.archiId());
        ViewNode note = view.placeAnnotation(ViewNodeKind.NOTE, Optional.empty(), Optional.of("Строка 1\nСтрока 2"),
                Bounds.ofNew(ViewNodeKind.NOTE, 20, 20, Optional.empty(), Optional.empty()), Optional.of(group.id()),
                ViewNodeId.next(UUIDS), Models.archiId());

        assertEquals(DiagramType.GROUP, group.archiType());
        assertEquals(Optional.empty(), group.elementId());
        assertEquals(Optional.of("Ядро"), group.label());
        assertEquals(new Bounds(0, 0, 400, 140), group.bounds(), "размер группы по умолчанию — как у Archi");
        assertEquals(Optional.of("Строка 1\nСтрока 2"), note.content());
        assertEquals(new Bounds(20, 20, 185, 80), note.bounds());
        assertEquals(Optional.of(group.id()), note.parentId());

        assertEquals("MDL_INVALID_INPUT", assertThrows(ModelingException.class, () -> view.placeAnnotation(
                ViewNodeKind.DIAGRAM_OBJECT, Optional.empty(), Optional.empty(), BOX, Optional.empty(),
                ViewNodeId.next(UUIDS), Models.archiId())).code(), "без элемента — только группа и заметка");
        assertEquals("MDL_INVALID_INPUT", assertThrows(ModelingException.class, () -> view.editNode(note.id(),
                Optional.of("Подпись"), Optional.empty(), Optional.empty())).code(), "у заметки нет подписи");
        assertEquals("INV-MDL-008", assertThrows(ModelingException.class, () -> view.placeAnnotation(
                ViewNodeKind.NOTE, Optional.empty(), Optional.empty(), BOX, Optional.of(note.id()),
                ViewNodeId.next(UUIDS), Models.archiId())).code(), "заметка не контейнер");

        ViewNode renamed = view.editNode(group.id(), Optional.of(""), Optional.empty(), Optional.empty());
        assertEquals(Optional.empty(), renamed.label(), "пустая подпись — подписи нет");
    }

    @Test
    @DisplayName("UC-MDL-008: стиль узла и ребра заменяется целиком, пустой — то же, что его отсутствие")
    void styleIsReplacedWhole() {
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        ViewNode node = view.placeElement(model, component.id(), BOX, Optional.empty(), ViewNodeId.next(UUIDS),
                Models.archiId()).value();
        StyleOverride red = new StyleOverride(Optional.of("#ff0000"), Optional.of("1|Arial|10.0|1|"),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertEquals(red, view.editNode(node.id(), Optional.empty(), Optional.empty(), Optional.of(red)).style());
        StyleOverride blueText = new StyleOverride(Optional.empty(), Optional.empty(), Optional.of("#0000ff"),
                Optional.empty(), Optional.empty());
        assertEquals(blueText, view.editNode(node.id(), Optional.empty(), Optional.empty(), Optional.of(blueText))
                .style(), "заливка и шрифт прежнего стиля не остаются");
        assertTrue(view.editNode(node.id(), Optional.empty(), Optional.empty(), Optional.of(StyleOverride.NONE))
                .style().empty());
        assertEquals(blueText, view.editNode(node.id(), Optional.empty(), Optional.empty(), Optional.of(blueText))
                .style());
        assertEquals(blueText, view.editNode(node.id(), Optional.empty(), Optional.empty(), Optional.empty()).style(),
                "без стиля в запросе стиль не меняется");
    }

    @Test
    @DisplayName("UC-MDL-008: удаление ребра оставляет связь в модели и убирает рёбра к нему")
    void removingEdgeKeepsRelationship() {
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");
        Element note = Models.element(model, "BusinessObject", FolderType.BUSINESS, "Договор");
        Relationship serving = Models.relate(model, "ServingRelationship", component.id(), actor.id());
        Relationship association = Models.relate(model, "AssociationRelationship", note.id(), serving.id());
        ViewNode c = view.placeElement(model, component.id(), BOX, Optional.empty(), ViewNodeId.next(UUIDS),
                Models.archiId()).value();
        ViewNode a = view.placeElement(model, actor.id(), new Bounds(300, 10, 120, 55), Optional.empty(),
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewNode n = view.placeElement(model, note.id(), new Bounds(150, 200, 120, 55), Optional.empty(),
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewEdge edge = view.connect(model, serving.id(), c.id(), a.id(), ViewEdgeId.next(UUIDS),
                Models.archiId()).value();
        view.connect(model, association.id(), n.id(), edge.id(), ViewEdgeId.next(UUIDS), Models.archiId());
        assertEquals("#00ff00", view.editEdge(edge.id(), new StyleOverride(Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of("#00ff00"), Optional.empty())).style().lineColor().orElseThrow());

        view.removeEdge(edge.id());

        assertEquals(0, view.edges().size(), "ребро к удалённому ребру уходит вместе с ним");
        assertEquals(3, view.nodes().size());
        assertTrue(model.relationships().contains(serving.id()));
        assertThrows(ModelingException.class, () -> view.removeEdge(edge.id()));
    }
}
