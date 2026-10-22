package uz.salvadore.hamkorbank.archi.modeling.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateFolderCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateViewCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RemoveViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CreateRelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.FolderResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlaceViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlaceViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/** Идентификаторы новых объектов от клиента и ребро существующей связи. */
class ClientIdsScenariosTest {

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    private static final EditorIdentity ALICE = EditorIdentity.of("alice", Role.ARCHITECT);
    private static final ArchiType COMPONENT = ArchiType.ofSimpleName("ApplicationComponent");
    private static final ArchiType ACTOR = ArchiType.ofSimpleName("BusinessActor");
    private static final ArchiType SERVING = ArchiType.ofSimpleName("ServingRelationship");

    private final InMemoryPorts ports = new InMemoryPorts();
    private ModelId model;

    @BeforeEach
    void lockedModel() {
        model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);
    }

    @Test
    @DisplayName("ADR-0018: id и archiId от клиента ложатся буквально — у папки, элемента, связи, представления, узла")
    void clientIdsAreKeptLiterally() {
        UUID folderId = IDS.generate();
        UUID elementId = IDS.generate();
        UUID viewId = IDS.generate();
        UUID nodeId = IDS.generate();
        UUID relationshipId = IDS.generate();
        UUID edgeId = IDS.generate();
        ModelFolder application = ports.models.get(model).roots().get(FolderType.APPLICATION);

        FolderResponse folder = ports.tree.createFolder(ALICE, new CreateFolderCommand(model.value(),
                application.id().value(), "Ядро", folderId, "id-folder-1", null));
        ElementResponse component = ports.elements.createElement(ALICE, new CreateElementCommand(model.value(),
                COMPONENT.value(), "АБС", folder.id(), elementId, "id-abs", null));
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");
        ViewSummaryResponse view = ports.viewService.createView(ALICE, new CreateViewCommand(model.value(),
                "Контекст", null, viewId, "id-view-1", null));
        ViewNodeResponse node = placeNode(view.id(), component.id(), new Bounds(10, 10, 120, 55), nodeId,
                "id-node-1").node();
        ViewNodeResponse actorNode = ports.place(ALICE, view.id(), actor.id(), new Bounds(300, 10, 120, 55));
        CreateRelationshipResponse serving = ports.relationships.createRelationship(ALICE,
                new CreateRelationshipCommand(model.value(), SERVING.value(), elementId, actor.id(), null, null, null,
                        null, relationshipId, "id-serving",
                        new CreateRelationshipCommand.Edge(view.id(), nodeId, actorNode.id(), edgeId, "id-edge-1"),
                        null));

        assertEquals(folderId, folder.id());
        assertEquals("id-folder-1", folder.archiId());
        assertEquals(elementId, component.id());
        assertEquals("id-abs", component.archiId());
        assertEquals(viewId, view.id());
        assertEquals("id-view-1", view.archiId());
        assertEquals(nodeId, node.id());
        assertEquals("id-node-1", node.archiId());
        assertEquals(relationshipId, serving.relationship().id());
        assertEquals("id-serving", serving.relationship().archiId());
        assertEquals(edgeId, serving.edgeId());
        assertEquals("id-edge-1", ports.views.get(ViewId.of(view.id())).requireEdge(ViewEdgeId.of(serving.edgeId()))
                .archiId().value());
    }

    @Test
    @DisplayName("ADR-0018: занятый id или archiId — 409 MDL_ID_TAKEN, недопустимый archiId — 422")
    void takenIdsAreRejected() {
        ElementResponse component = createElement("АБС", IDS.generate(), "id-abs", null);
        ViewSummaryResponse view = ports.view(ALICE, model, "Контекст");
        ViewNodeResponse node = ports.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55));

        ModelingDomainException sameId = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", component.id(), null, null));
        assertEquals("MDL_ID_TAKEN", sameId.code());
        assertEquals(Failure.CONFLICT, sameId.failure());

        ModelingDomainException idOfNode = assertThrows(ModelingDomainException.class,
                () -> ports.viewService.createView(ALICE, new CreateViewCommand(model.value(), "Другое", null,
                        node.id(), null, null)));
        assertEquals("MDL_ID_TAKEN", idOfNode.code(), "ключ узла тоже занят");

        ModelingDomainException sameArchiId = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", null, "id-abs", null));
        assertEquals("MDL_ID_TAKEN", sameArchiId.code());

        ModelingDomainException archiIdOfNode = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", null, node.archiId(), null));
        assertEquals("MDL_ID_TAKEN", archiIdOfNode.code(), "archiId узла на представлении — тоже занят в файле");

        ModelingDomainException invalid = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", null, "не id", null));
        assertEquals(ModelingCodes.INVALID_INPUT, invalid.code());
        assertEquals(Failure.UNPROCESSABLE, invalid.failure());
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");
        ViewNodeResponse actorNode = ports.place(ALICE, view.id(), actor.id(), new Bounds(300, 10, 120, 55));
        ModelingDomainException edgeIdOfNode = assertThrows(ModelingDomainException.class,
                () -> ports.relationships.createRelationship(ALICE, new CreateRelationshipCommand(model.value(),
                        SERVING.value(), component.id(), actor.id(), null, null, null, null, null, null,
                        new CreateRelationshipCommand.Edge(view.id(), node.id(), actorNode.id(), node.id(), null),
                        null)));
        assertEquals("MDL_ID_TAKEN", edgeIdOfNode.code(), "ключ ребра занят узлом — уже после записи связи");

        assertEquals(2, ports.models.get(model).elements().size(), "отказ ничего не создал");
        assertTrue(ports.models.get(model).relationships().values().isEmpty(), "связь до отказа откатилась");
    }

    @Test
    @DisplayName("ADR-0018: повтор с тем же ключом идемпотентности и теми же id — тот же объект, не 409")
    void idempotentReplayWithClientIds() {
        UUID elementId = IDS.generate();
        ElementResponse first = createElement("АБС", elementId, "id-abs", "k-1");
        ElementResponse replay = createElement("АБС", elementId, "id-abs", "k-1");

        assertEquals(first.id(), replay.id());
        ModelingDomainException otherBody = assertThrows(ModelingDomainException.class,
                () -> createElement("АБС", IDS.generate(), "id-abs", "k-1"));
        assertEquals("INV-MDL-003", otherBody.code(), "другие id — другое тело команды");
    }

    @Test
    @DisplayName("UC-MDL-002: отмена удаления узла возвращает его рёбра — ребро существующей связи по тем же id")
    void edgeOfExistingRelationshipIsPlacedBack() {
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");
        UUID serving = ports.relate(ALICE, model, SERVING, component.id(), actor.id()).id();
        ViewSummaryResponse view = ports.view(ALICE, model, "Контекст");
        ViewNodeResponse c = ports.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55));
        ViewNodeResponse a = ports.place(ALICE, view.id(), actor.id(), new Bounds(300, 10, 120, 55));
        List<BendpointValue> bend = List.of(new BendpointValue(10, 40, -150, 40));
        ViewEdgeResponse edge = placeEdge(view.id(), serving, c.id(), a.id(), bend, null, null).edge();

        ports.viewService.removeNode(ALICE, new RemoveViewNodeCommand(c.id()));
        assertTrue(ports.views.get(ViewId.of(view.id())).edges().values().isEmpty(), "ребро ушло вместе с узлом");

        ViewNodeResponse restored = placeNode(view.id(), component.id(),
                new Bounds(c.x(), c.y(), c.width(), c.height()), c.id(), c.archiId()).node();
        PlaceViewEdgeResponse again = placeEdge(view.id(), serving, restored.id(), a.id(), edge.bendpoints(),
                edge.id(), edge.archiId());

        assertTrue(again.created());
        assertEquals(c.id(), restored.id());
        assertEquals(edge.id(), again.edge().id());
        assertEquals(edge.archiId(), again.edge().archiId());
        assertEquals(bend, again.edge().bendpoints());
        assertEquals(restored.id(), again.edge().sourceId(), "ребро лежит в содержимом узла-источника");

        PlaceViewEdgeResponse duplicate = placeEdge(view.id(), serving, restored.id(), a.id(), List.of(), null, null);
        assertFalse(duplicate.created(), "то же ребро между теми же концами не дублируется");
        assertEquals(edge.id(), duplicate.edge().id());
    }

    @Test
    @DisplayName("INV-MDL-008: концы ребра не изображают концы связи — 422")
    void edgeEndsMustDepictRelationshipEnds() {
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");
        UUID serving = ports.relate(ALICE, model, SERVING, component.id(), actor.id()).id();
        ViewSummaryResponse view = ports.view(ALICE, model, "Контекст");
        ViewNodeResponse c = ports.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55));
        ViewNodeResponse a = ports.place(ALICE, view.id(), actor.id(), new Bounds(300, 10, 120, 55));

        ModelingDomainException reversed = assertThrows(ModelingDomainException.class,
                () -> placeEdge(view.id(), serving, a.id(), c.id(), List.of(), null, null));
        assertEquals("INV-MDL-008", reversed.code());
        assertEquals(Failure.UNPROCESSABLE, reversed.failure());

        ModelingDomainException foreign = assertThrows(ModelingDomainException.class,
                () -> placeEdge(view.id(), serving, IDS.generate(), a.id(), List.of(), null, null));
        assertEquals("INV-MDL-008", foreign.code(), "конец не на этом представлении");
    }

    @Test
    @DisplayName("UC-MDL-003: связь в подпапке Relations, вид доступа у Access, направленность у Association")
    void relationshipFolderAndTypeAttributes() {
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse data = ports.element(ALICE, model, ArchiType.ofSimpleName("DataObject"), "Счёт");
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");
        ModelFolder relations = ports.models.get(model).roots().get(FolderType.RELATIONS);
        FolderResponse sub = ports.tree.createFolder(ALICE, new CreateFolderCommand(model.value(),
                relations.id().value(), "Потоки", null, null, null));

        RelationshipResponse access = createRelationship(ArchiType.ofSimpleName("AccessRelationship"),
                component.id(), data.id(), sub.id(), "READ", null);
        RelationshipResponse association = createRelationship(ArchiType.ofSimpleName("AssociationRelationship"),
                component.id(), actor.id(), null, null, true);

        assertEquals(sub.id(), access.folderId());
        assertEquals("READ", access.accessType());
        assertEquals(relations.id().value(), association.folderId());
        assertEquals(true, association.directed());

        ModelingDomainException wrongAttribute = assertThrows(ModelingDomainException.class,
                () -> createRelationship(SERVING, component.id(), actor.id(), null, "READ", null));
        assertEquals(Failure.UNPROCESSABLE, wrongAttribute.failure());

        ModelingDomainException unknownAccess = assertThrows(ModelingDomainException.class,
                () -> createRelationship(ArchiType.ofSimpleName("AccessRelationship"), component.id(), data.id(),
                        null, "SOMETIMES", null));
        assertEquals(ModelingCodes.INVALID_INPUT, unknownAccess.code(), "вид доступа разбирает сценарий");

        ModelFolder application = ports.models.get(model).roots().get(FolderType.APPLICATION);
        ModelingDomainException wrongFolder = assertThrows(ModelingDomainException.class,
                () -> createRelationship(SERVING, component.id(), actor.id(), application.id().value(), null,
                        null));
        assertEquals("INV-MDL-009", wrongFolder.code());
    }

    private ElementResponse createElement(String name, UUID id, String archiId, String key) {
        return ports.elements.createElement(ALICE, new CreateElementCommand(model.value(), COMPONENT.value(), name,
                null, id, archiId, key));
    }

    private RelationshipResponse createRelationship(ArchiType type, UUID source, UUID target, UUID folderId,
                                                    String accessType, Boolean directed) {
        return ports.relationships.createRelationship(ALICE, new CreateRelationshipCommand(model.value(),
                type.value(), source, target, null, folderId, accessType, directed, null, null, null, null))
                .relationship();
    }

    private PlaceViewNodeResponse placeNode(UUID view, UUID element, Bounds bounds, UUID id, String archiId) {
        return ports.viewService.placeNode(ALICE, new PlaceViewNodeCommand(view, null, element, null, null,
                bounds.x(), bounds.y(), bounds.width(), bounds.height(), null, id, archiId));
    }

    private PlaceViewEdgeResponse placeEdge(UUID view, UUID relationship, UUID source, UUID target,
                                            List<BendpointValue> bendpoints, UUID id, String archiId) {
        return ports.viewService.placeEdge(ALICE, new PlaceViewEdgeCommand(view, relationship, source, target,
                bendpoints, id, archiId));
    }
}
