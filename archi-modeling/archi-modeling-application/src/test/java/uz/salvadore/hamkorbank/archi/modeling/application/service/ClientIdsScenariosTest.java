package uz.salvadore.hamkorbank.archi.modeling.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;

/** Идентификаторы новых объектов от клиента и ребро существующей связи. */
class ClientIdsScenariosTest {

    private static final EditorIdentity ALICE = EditorIdentity.of("alice", Role.ARCHITECT);
    private static final ArchiType COMPONENT = ArchiType.ofSimpleName("ApplicationComponent");
    private static final ArchiType ACTOR = ArchiType.ofSimpleName("BusinessActor");
    private static final ArchiType SERVING = ArchiType.ofSimpleName("ServingRelationship");

    private final InMemoryPorts ports = new InMemoryPorts();
    private ModelId model;

    @BeforeEach
    void lockedModel() {
        model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", Optional.empty()).id();
        ports.lockService.acquire(ALICE, model);
    }

    @Test
    @DisplayName("ADR-0018: id и archiId от клиента ложатся буквально — у папки, элемента, связи, представления, узла")
    void clientIdsAreKeptLiterally() {
        UUID folderId = UUID.randomUUID();
        UUID elementId = UUID.randomUUID();
        UUID viewId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UUID edgeId = UUID.randomUUID();
        ModelFolder application = ports.models.get(model).roots().get(FolderType.APPLICATION);

        ModelFolder folder = ports.tree.createFolder(ALICE, model, application.id(), "Ядро",
                RequestedIds.of(folderId, "id-folder-1"), Optional.empty());
        Element component = ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.of(folder.id()),
                RequestedIds.of(elementId, "id-abs"), Optional.empty());
        Element actor = ports.elements.create(ALICE, model, ACTOR, "Клиент", Optional.empty(), Optional.empty());
        View view = ports.viewService.create(ALICE, model, "Контекст", Optional.empty(),
                RequestedIds.of(viewId, "id-view-1"), Optional.empty());
        ViewNode node = ports.viewService.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55),
                Optional.empty(), RequestedIds.of(nodeId, "id-node-1")).value();
        ViewNode actorNode = ports.viewService.place(ALICE, view.id(), actor.id(), new Bounds(300, 10, 120, 55),
                Optional.empty());
        var serving = ports.relationships.create(ALICE, model, SERVING, elementId, actor.id().value(),
                new RelationshipService.Details(Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), RequestedIds.of(relationshipId, "id-serving")),
                Optional.of(new RelationshipService.EdgePlacement(view.id(), nodeId, actorNode.id().value(),
                        RequestedIds.of(edgeId, "id-edge-1"))),
                Optional.empty());

        assertEquals(folderId, folder.id().value());
        assertEquals("id-folder-1", folder.archiId().value());
        assertEquals(elementId, component.id().value());
        assertEquals("id-abs", component.archiId().value());
        assertEquals(viewId, view.id().value());
        assertEquals("id-view-1", view.archiId().value());
        assertEquals(nodeId, node.id().value());
        assertEquals("id-node-1", node.archiId().value());
        assertEquals(relationshipId, serving.relationship().id().value());
        assertEquals("id-serving", serving.relationship().archiId().value());
        assertEquals(edgeId, serving.edge().orElseThrow().value());
        assertEquals("id-edge-1", ports.views.get(view.id()).requireEdge(serving.edge().orElseThrow())
                .archiId().value());
    }

    @Test
    @DisplayName("ADR-0018: занятый id или archiId — 409 MDL_ID_TAKEN, недопустимый archiId — 422")
    void takenIdsAreRejected() {
        Element component = ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.empty(),
                RequestedIds.of(UUID.randomUUID(), "id-abs"), Optional.empty());
        View view = ports.viewService.create(ALICE, model, "Контекст", Optional.empty(), Optional.empty());
        ViewNode node = ports.viewService.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55),
                Optional.empty());

        ModelingException sameId = assertThrows(ModelingException.class, () -> ports.elements.create(ALICE, model,
                COMPONENT, "CRM", Optional.empty(), RequestedIds.of(component.id().value(), null), Optional.empty()));
        assertEquals("MDL_ID_TAKEN", sameId.code());
        assertEquals(Failure.CONFLICT, sameId.failure());

        ModelingException idOfNode = assertThrows(ModelingException.class, () -> ports.viewService.create(ALICE,
                model, "Другое", Optional.empty(), RequestedIds.of(node.id().value(), null), Optional.empty()));
        assertEquals("MDL_ID_TAKEN", idOfNode.code(), "ключ узла тоже занят");

        ModelingException sameArchiId = assertThrows(ModelingException.class, () -> ports.elements.create(ALICE,
                model, COMPONENT, "CRM", Optional.empty(), RequestedIds.of(null, "id-abs"), Optional.empty()));
        assertEquals("MDL_ID_TAKEN", sameArchiId.code());

        ModelingException archiIdOfNode = assertThrows(ModelingException.class, () -> ports.elements.create(ALICE,
                model, COMPONENT, "CRM", Optional.empty(), RequestedIds.of(null, node.archiId().value()),
                Optional.empty()));
        assertEquals("MDL_ID_TAKEN", archiIdOfNode.code(), "archiId узла на представлении — тоже занят в файле");

        ModelingException invalid = assertThrows(ModelingException.class, () -> ports.elements.create(ALICE, model,
                COMPONENT, "CRM", Optional.empty(), RequestedIds.of(null, "не id"), Optional.empty()));
        assertEquals(ModelingCodes.INVALID_INPUT, invalid.code());
        assertEquals(Failure.UNPROCESSABLE, invalid.failure());
        assertEquals(1, ports.models.get(model).elements().size(), "отказ ничего не создал");
    }

    @Test
    @DisplayName("ADR-0018: повтор с тем же ключом идемпотентности и теми же id — тот же объект, не 409")
    void idempotentReplayWithClientIds() {
        UUID elementId = UUID.randomUUID();
        Element first = ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.empty(),
                RequestedIds.of(elementId, "id-abs"), Optional.of("k-1"));
        Element replay = ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.empty(),
                RequestedIds.of(elementId, "id-abs"), Optional.of("k-1"));

        assertEquals(first.id(), replay.id());
        ModelingException otherBody = assertThrows(ModelingException.class, () -> ports.elements.create(ALICE,
                model, COMPONENT, "АБС", Optional.empty(), RequestedIds.of(UUID.randomUUID(), "id-abs"),
                Optional.of("k-1")));
        assertEquals("INV-MDL-003", otherBody.code(), "другие id — другое тело команды");
    }

    @Test
    @DisplayName("UC-MDL-002: отмена удаления узла возвращает его рёбра — ребро существующей связи по тем же id")
    void edgeOfExistingRelationshipIsPlacedBack() {
        Element component = ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.empty(), Optional.empty());
        Element actor = ports.elements.create(ALICE, model, ACTOR, "Клиент", Optional.empty(), Optional.empty());
        Relationship serving = ports.relationships.create(ALICE, model, SERVING, component.id().value(),
                actor.id().value(), Optional.empty(), Optional.empty(), Optional.empty()).relationship();
        View view = ports.viewService.create(ALICE, model, "Контекст", Optional.empty(), Optional.empty());
        ViewNode c = ports.viewService.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55),
                Optional.empty());
        ViewNode a = ports.viewService.place(ALICE, view.id(), actor.id(), new Bounds(300, 10, 120, 55),
                Optional.empty());
        List<Bendpoint> bend = List.of(new Bendpoint(10, 40, -150, 40));
        ViewEdge edge = ports.viewService.placeEdge(ALICE, view.id(), serving.id(), c.id().value(), a.id().value(),
                bend, RequestedIds.NONE).value();

        ports.viewService.removeNode(ALICE, c.id());
        assertTrue(ports.views.get(view.id()).edges().values().isEmpty(), "ребро ушло вместе с узлом");

        ViewNode restored = ports.viewService.place(ALICE, view.id(), component.id(), c.bounds(), Optional.empty(),
                RequestedIds.of(c.id().value(), c.archiId().value())).value();
        var again = ports.viewService.placeEdge(ALICE, view.id(), serving.id(), restored.id().value(),
                a.id().value(), edge.bendpoints(), RequestedIds.of(edge.id().value(), edge.archiId().value()));

        assertTrue(again.created());
        assertEquals(c.id(), restored.id());
        assertEquals(edge.id(), again.value().id());
        assertEquals(edge.archiId(), again.value().archiId());
        assertEquals(bend, again.value().bendpoints());
        assertEquals(restored.id(), again.value().source(), "ребро лежит в содержимом узла-источника");

        var duplicate = ports.viewService.placeEdge(ALICE, view.id(), serving.id(), restored.id().value(),
                a.id().value(), List.of(), RequestedIds.NONE);
        assertFalse(duplicate.created(), "то же ребро между теми же концами не дублируется");
        assertEquals(edge.id(), duplicate.value().id());
    }

    @Test
    @DisplayName("INV-MDL-008: концы ребра не изображают концы связи — 422")
    void edgeEndsMustDepictRelationshipEnds() {
        Element component = ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.empty(), Optional.empty());
        Element actor = ports.elements.create(ALICE, model, ACTOR, "Клиент", Optional.empty(), Optional.empty());
        Relationship serving = ports.relationships.create(ALICE, model, SERVING, component.id().value(),
                actor.id().value(), Optional.empty(), Optional.empty(), Optional.empty()).relationship();
        View view = ports.viewService.create(ALICE, model, "Контекст", Optional.empty(), Optional.empty());
        ViewNode c = ports.viewService.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55),
                Optional.empty());
        ViewNode a = ports.viewService.place(ALICE, view.id(), actor.id(), new Bounds(300, 10, 120, 55),
                Optional.empty());

        ModelingException reversed = assertThrows(ModelingException.class, () -> ports.viewService.placeEdge(ALICE,
                view.id(), serving.id(), a.id().value(), c.id().value(), List.of(), RequestedIds.NONE));
        assertEquals("INV-MDL-008", reversed.code());
        assertEquals(Failure.UNPROCESSABLE, reversed.failure());

        ModelingException foreign = assertThrows(ModelingException.class, () -> ports.viewService.placeEdge(ALICE,
                view.id(), serving.id(), UUID.randomUUID(), a.id().value(), List.of(), RequestedIds.NONE));
        assertEquals("INV-MDL-008", foreign.code(), "конец не на этом представлении");
    }

    @Test
    @DisplayName("UC-MDL-003: связь в подпапке Relations, вид доступа у Access, направленность у Association")
    void relationshipFolderAndTypeAttributes() {
        Element component = ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.empty(), Optional.empty());
        Element data = ports.elements.create(ALICE, model, ArchiType.ofSimpleName("DataObject"), "Счёт",
                Optional.empty(), Optional.empty());
        Element actor = ports.elements.create(ALICE, model, ACTOR, "Клиент", Optional.empty(), Optional.empty());
        ModelFolder relations = ports.models.get(model).roots().get(FolderType.RELATIONS);
        ModelFolder sub = ports.tree.createFolder(ALICE, model, relations.id(), "Потоки", Optional.empty());

        Relationship access = ports.relationships.create(ALICE, model, ArchiType.ofSimpleName("AccessRelationship"),
                component.id().value(), data.id().value(), new RelationshipService.Details(Optional.empty(),
                        Optional.of(sub.id()), Optional.of(AccessType.READ), Optional.empty(), RequestedIds.NONE),
                Optional.empty(), Optional.empty()).relationship();
        Relationship association = ports.relationships.create(ALICE, model,
                ArchiType.ofSimpleName("AssociationRelationship"), component.id().value(), actor.id().value(),
                new RelationshipService.Details(Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of(true), RequestedIds.NONE),
                Optional.empty(), Optional.empty()).relationship();

        assertEquals(sub.id(), access.folderId());
        assertEquals(Optional.of(AccessType.READ), access.accessType());
        assertEquals(relations.id(), association.folderId());
        assertEquals(Optional.of(true), association.directed());

        ModelingException wrongAttribute = assertThrows(ModelingException.class, () -> ports.relationships.create(
                ALICE, model, SERVING, component.id().value(), actor.id().value(),
                new RelationshipService.Details(Optional.empty(), Optional.empty(), Optional.of(AccessType.READ),
                        Optional.empty(), RequestedIds.NONE),
                Optional.empty(), Optional.empty()));
        assertEquals(Failure.UNPROCESSABLE, wrongAttribute.failure());

        ModelFolder application = ports.models.get(model).roots().get(FolderType.APPLICATION);
        ModelingException wrongFolder = assertThrows(ModelingException.class, () -> ports.relationships.create(
                ALICE, model, SERVING, component.id().value(), actor.id().value(),
                new RelationshipService.Details(Optional.empty(), Optional.of(application.id()), Optional.empty(),
                        Optional.empty(), RequestedIds.NONE),
                Optional.empty(), Optional.empty()));
        assertEquals("INV-MDL-009", wrongFolder.code());
    }
}
