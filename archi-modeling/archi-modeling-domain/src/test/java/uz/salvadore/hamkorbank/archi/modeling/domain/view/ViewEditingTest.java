package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
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
        ViewNode server = view.placeElement(model, node.id(), new Bounds(0, 0, 400, 300), null,
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewNode inside = view.placeElement(model, device.id(), BOX, server.id(),
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewNode dbms = view.placeElement(model, software.id(), new Bounds(500, 40, 120, 55), null,
                ViewNodeId.next(UUIDS), Models.archiId()).value();

        ViewNode moved = view.moveToParent(dbms.id(), server.id(), 200, 40);

        assertEquals(server.id(), moved.parentId());
        assertEquals(new Bounds(200, 40, 120, 55), moved.bounds(), "координаты — относительно контейнера");
        assertTrue(moved.sortOrder().value() > inside.sortOrder().value(), "в конце содержимого контейнера");
        assertEquals(inside.sortOrder(), view.requireNode(inside.id()).sortOrder(), "соседи не перенумерованы");

        ViewNode back = view.moveToParent(dbms.id(), null, 700, 40);
        assertNull(back.parentId());
        assertEquals("INV-MDL-008", assertThrows(ModelingDomainException.class,
                () -> view.moveToParent(server.id(), inside.id(), 0, 0)).code(), "контейнер внутри себя");
        assertEquals("INV-MDL-008", assertThrows(ModelingDomainException.class,
                () -> view.moveToParent(server.id(), server.id(), 0, 0)).code());

        ViewNode same = view.moveToParent(inside.id(), server.id(), 30, 30);
        assertEquals(inside.sortOrder(), same.sortOrder(), "в том же родителе место не меняется");
    }

    @Test
    @DisplayName("UC-MDL-008: группа и заметка — узлы без элемента, подпись у группы, текст у заметки")
    void groupAndNoteHaveNoElement() {
        ViewNode group = view.placeAnnotation(ViewNodeKind.GROUP, "Ядро", null,
                Bounds.ofNew(ViewNodeKind.GROUP, 0, 0, null, null), null,
                ViewNodeId.next(UUIDS), Models.archiId());
        ViewNode note = view.placeAnnotation(ViewNodeKind.NOTE, null, "Строка 1\nСтрока 2",
                Bounds.ofNew(ViewNodeKind.NOTE, 20, 20, null, null), group.id(),
                ViewNodeId.next(UUIDS), Models.archiId());

        assertEquals(DiagramType.GROUP, group.archiType());
        assertNull(group.elementId());
        assertEquals("Ядро", group.label());
        assertEquals(new Bounds(0, 0, 400, 140), group.bounds(), "размер группы по умолчанию — как у Archi");
        assertEquals("Строка 1\nСтрока 2", note.content());
        assertEquals(new Bounds(20, 20, 185, 80), note.bounds());
        assertEquals(group.id(), note.parentId());

        assertEquals("MDL_INVALID_INPUT", assertThrows(ModelingDomainException.class, () -> view.placeAnnotation(
                ViewNodeKind.DIAGRAM_OBJECT, null, null, BOX, null,
                ViewNodeId.next(UUIDS), Models.archiId())).code(), "без элемента — только группа и заметка");
        assertEquals("MDL_INVALID_INPUT", assertThrows(ModelingDomainException.class, () -> view.editNode(note.id(),
                "Подпись", null, null)).code(), "у заметки нет подписи");
        assertEquals("INV-MDL-008", assertThrows(ModelingDomainException.class, () -> view.placeAnnotation(
                ViewNodeKind.NOTE, null, null, BOX, note.id(),
                ViewNodeId.next(UUIDS), Models.archiId())).code(), "заметка не контейнер");

        ViewNode renamed = view.editNode(group.id(), "", null, null);
        assertNull(renamed.label(), "пустая подпись — подписи нет");
    }

    @Test
    @DisplayName("UC-MDL-008: стиль узла и ребра заменяется целиком, пустой — то же, что его отсутствие")
    void styleIsReplacedWhole() {
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        ViewNode node = view.placeElement(model, component.id(), BOX, null, ViewNodeId.next(UUIDS),
                Models.archiId()).value();
        StyleOverride red = new StyleOverride("#ff0000", "1|Arial|10.0|1|",
                null, null, null);

        assertEquals(red, view.editNode(node.id(), null, null, red).style());
        StyleOverride blueText = new StyleOverride(null, null, "#0000ff",
                null, null);
        assertEquals(blueText, view.editNode(node.id(), null, null, blueText)
                .style(), "заливка и шрифт прежнего стиля не остаются");
        assertTrue(view.editNode(node.id(), null, null, StyleOverride.NONE)
                .style().empty());
        assertEquals(blueText, view.editNode(node.id(), null, null, blueText)
                .style());
        assertEquals(blueText, view.editNode(node.id(), null, null, null).style(),
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
        ViewNode c = view.placeElement(model, component.id(), BOX, null, ViewNodeId.next(UUIDS),
                Models.archiId()).value();
        ViewNode a = view.placeElement(model, actor.id(), new Bounds(300, 10, 120, 55), null,
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewNode n = view.placeElement(model, note.id(), new Bounds(150, 200, 120, 55), null,
                ViewNodeId.next(UUIDS), Models.archiId()).value();
        ViewEdge edge = view.connect(model, serving.id(), c.id(), a.id(), ViewEdgeId.next(UUIDS),
                Models.archiId()).value();
        view.connect(model, association.id(), n.id(), edge.id(), ViewEdgeId.next(UUIDS), Models.archiId());
        assertEquals("#00ff00", view.editEdge(edge.id(), new StyleOverride(null, null,
                null, "#00ff00", null)).style().lineColor());

        view.removeEdge(edge.id());

        assertEquals(0, view.edges().size(), "ребро к удалённому ребру уходит вместе с ним");
        assertEquals(3, view.nodes().size());
        assertTrue(model.relationships().contains(serving.id()));
        assertThrows(ModelingDomainException.class, () -> view.removeEdge(edge.id()));
    }

    @Test
    @DisplayName("UC-MDL-008: разметка с чужим узлом не меняет и остальные узлы")
    void layoutWithUnknownNodeChangesNothing() {
        Element element = Models.element(model, "Node", FolderType.TECHNOLOGY, "Сервер");
        ViewNode node = view.placeElement(model, element.id(), BOX, null, ViewNodeId.next(UUIDS),
                Models.archiId()).value();
        view.markPersisted();
        Map<ViewNodeId, Bounds> layout = new LinkedHashMap<>();
        layout.put(node.id(), new Bounds(400, 40, 120, 55));
        layout.put(ViewNodeId.next(UUIDS), new Bounds(0, 0, 120, 55));

        assertThrows(ModelingDomainException.class, () -> view.applyLayout(layout, Map.of()));

        assertEquals(BOX, view.requireNode(node.id()).bounds());
        assertFalse(view.nodes().changed(), "отклонённая разметка ничего не оставила для записи");
    }

    @Test
    @DisplayName("UC-MDL-008: текст бывает только у заметки — у группы он отклоняется")
    void contentOnGroupIsRejected() {
        ViewNode group = view.placeAnnotation(ViewNodeKind.GROUP, "Ядро", null, BOX, null,
                ViewNodeId.next(UUIDS), Models.archiId());

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> view.editNode(group.id(), null, "Текст", null));

        assertEquals(ModelingCodes.INVALID_INPUT, rejected.code());
        assertEquals(ModelingMessages.NODE_TEXT_NOT_APPLICABLE, rejected.reason().key());
        assertNull(view.requireNode(group.id()).content());
    }

    @Test
    @DisplayName("FR-08: представление не-ArchiMate хранится, но не правится")
    void sketchViewIsNotEditable() {
        View sketch = View.restore(new ViewHeader(ViewId.next(UUIDS), model.id(), FolderId.next(UUIDS),
                Models.archiId(), new DiagramType("archimate:SketchModel"), "Набросок", null, null, List.of(),
                SortOrder.ofPosition(0), null, 0), List.of(), List.of());

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> sketch.placeAnnotation(ViewNodeKind.GROUP, "Ядро", null, BOX, null,
                        ViewNodeId.next(UUIDS), Models.archiId()));

        assertEquals(ModelingCodes.TYPE_NOT_EDITABLE, rejected.code());
        assertEquals(ModelingMessages.VIEW_NOT_EDITABLE, rejected.reason().key());
        assertEquals(0, sketch.nodes().size());
    }

    @Test
    @DisplayName("INV-MDL-008: ребро соединяет только узлы этого представления")
    void edgeToEndpointOutsideViewIsRejected() {
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");
        Relationship serving = Models.relate(model, "ServingRelationship", component.id(), actor.id());
        ViewNode a = view.placeElement(model, actor.id(), BOX, null, ViewNodeId.next(UUIDS),
                Models.archiId()).value();

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> view.connect(model, serving.id(), ViewNodeId.next(UUIDS), a.id(), ViewEdgeId.next(UUIDS),
                        Models.archiId()));

        assertEquals(ModelingCodes.VIEW_REFERENCE, rejected.code());
        assertEquals(ModelingMessages.ENDPOINT_ELSEWHERE, rejected.reason().key());
        assertEquals(0, view.edges().size());
    }

    @Test
    @DisplayName("INV-MDL-001: новый узел не берёт archi_id, занятый на представлении")
    void nodeWithTakenArchiIdIsRejected() {
        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> view.placeAnnotation(ViewNodeKind.GROUP, "Ядро", null, BOX, null,
                        ViewNodeId.next(UUIDS), view.archiId()));

        assertEquals(ModelingCodes.ARCHI_ID_UNIQUE, rejected.code());
        assertEquals(Failure.CONFLICT, rejected.failure());
        assertEquals(ModelingMessages.ARCHI_ID_TAKEN_ON_VIEW, rejected.reason().key());
        assertEquals(0, view.nodes().size());
    }
}
