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
    private static final List<BendpointValue> BEND = List.of(new BendpointValue(10, 40, -150, 40));

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
    @DisplayName("ADR-0018: занятый id элемента — 409 MDL_ID_TAKEN")
    void takenElementIdIsRejected() {
        Placed placed = placed();

        ModelingDomainException sameId = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", placed.component().id(), null, null));

        assertEquals("MDL_ID_TAKEN", sameId.code());
        assertEquals(Failure.CONFLICT, sameId.failure());
    }

    @Test
    @DisplayName("ADR-0018: id узла представления занят и для нового представления — 409 MDL_ID_TAKEN")
    void nodeIdIsTakenForView() {
        Placed placed = placed();

        ModelingDomainException idOfNode = assertThrows(ModelingDomainException.class,
                () -> ports.viewService.createView(ALICE, new CreateViewCommand(model.value(), "Другое", null,
                        placed.node().id(), null, null)));

        assertEquals("MDL_ID_TAKEN", idOfNode.code(), "ключ узла тоже занят");
    }

    @Test
    @DisplayName("ADR-0018: занятый archiId элемента — 409 MDL_ID_TAKEN")
    void takenArchiIdIsRejected() {
        placed();

        ModelingDomainException sameArchiId = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", null, "id-abs", null));

        assertEquals("MDL_ID_TAKEN", sameArchiId.code());
    }

    @Test
    @DisplayName("ADR-0018: archiId узла на представлении занят и для элемента — 409 MDL_ID_TAKEN")
    void nodeArchiIdIsTakenForElement() {
        Placed placed = placed();

        ModelingDomainException archiIdOfNode = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", null, placed.node().archiId(), null));

        assertEquals("MDL_ID_TAKEN", archiIdOfNode.code(), "archiId узла на представлении — тоже занят в файле");
    }

    @Test
    @DisplayName("ADR-0018: недопустимый archiId — 422")
    void invalidArchiIdIsRejected() {
        placed();

        ModelingDomainException invalid = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", null, "не id", null));

        assertEquals(ModelingCodes.INVALID_INPUT, invalid.code());
        assertEquals(Failure.UNPROCESSABLE, invalid.failure());
    }

    @Test
    @DisplayName("ADR-0018: id ребра занят узлом — 409 MDL_ID_TAKEN, связь до отказа откатывается")
    void takenEdgeIdRollsBackRelationship() {
        Placed placed = placed();
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");
        ViewNodeResponse actorNode = ports.place(ALICE, placed.view().id(), actor.id(),
                new Bounds(300, 10, 120, 55));

        ModelingDomainException edgeIdOfNode = assertThrows(ModelingDomainException.class,
                () -> ports.relationships.createRelationship(ALICE, new CreateRelationshipCommand(model.value(),
                        SERVING.value(), placed.component().id(), actor.id(), null, null, null, null, null, null,
                        new CreateRelationshipCommand.Edge(placed.view().id(), placed.node().id(), actorNode.id(),
                                placed.node().id(), null),
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
    }

    @Test
    @DisplayName("INV-MDL-003: тот же ключ идемпотентности с другими id — другое тело команды, отказ")
    void replayWithOtherIdsIsRejected() {
        createElement("АБС", IDS.generate(), "id-abs", "k-1");

        ModelingDomainException otherBody = assertThrows(ModelingDomainException.class,
                () -> createElement("АБС", IDS.generate(), "id-abs", "k-1"));

        assertEquals("INV-MDL-003", otherBody.code(), "другие id — другое тело команды");
    }

    @Test
    @DisplayName("UC-MDL-002: удаление узла убирает рёбра, которые к нему подходят")
    void removingNodeRemovesItsEdges() {
        Drawn drawn = drawn();

        ports.viewService.removeNode(ALICE, new RemoveViewNodeCommand(drawn.source().id()));

        assertTrue(ports.views.get(ViewId.of(drawn.view())).edges().values().isEmpty(), "ребро ушло вместе с узлом");
    }

    @Test
    @DisplayName("UC-MDL-002: отмена удаления узла возвращает его рёбра — ребро существующей связи по тем же id")
    void edgeOfExistingRelationshipIsPlacedBack() {
        Drawn drawn = drawn();
        ViewNodeResponse c = drawn.source();
        ports.viewService.removeNode(ALICE, new RemoveViewNodeCommand(c.id()));

        ViewNodeResponse restored = placeNode(drawn.view(), c.elementId(),
                new Bounds(c.x(), c.y(), c.width(), c.height()), c.id(), c.archiId()).node();
        PlaceViewEdgeResponse again = placeEdge(drawn.view(), drawn.serving(), restored.id(), drawn.target().id(),
                drawn.edge().bendpoints(), drawn.edge().id(), drawn.edge().archiId());

        assertTrue(again.created());
        assertEquals(c.id(), restored.id());
        assertEquals(drawn.edge().id(), again.edge().id());
        assertEquals(drawn.edge().archiId(), again.edge().archiId());
        assertEquals(BEND, again.edge().bendpoints());
        assertEquals(restored.id(), again.edge().sourceId(), "ребро лежит в содержимом узла-источника");
    }

    @Test
    @DisplayName("UC-MDL-002: то же ребро между теми же концами не дублируется")
    void sameEdgeIsNotDuplicated() {
        Drawn drawn = drawn();

        PlaceViewEdgeResponse duplicate = placeEdge(drawn.view(), drawn.serving(), drawn.source().id(),
                drawn.target().id(), List.of(), null, null);

        assertFalse(duplicate.created(), "то же ребро между теми же концами не дублируется");
        assertEquals(drawn.edge().id(), duplicate.edge().id());
    }

    @Test
    @DisplayName("INV-MDL-008: концы ребра в обратном порядке не изображают концы связи — 422")
    void reversedEdgeEndsAreRejected() {
        Drawn drawn = undrawn();

        ModelingDomainException reversed = assertThrows(ModelingDomainException.class,
                () -> placeEdge(drawn.view(), drawn.serving(), drawn.target().id(), drawn.source().id(), List.of(),
                        null, null));

        assertEquals("INV-MDL-008", reversed.code());
        assertEquals(Failure.UNPROCESSABLE, reversed.failure());
    }

    @Test
    @DisplayName("INV-MDL-008: конец ребра не на этом представлении — 422")
    void edgeEndOutsideViewIsRejected() {
        Drawn drawn = undrawn();

        ModelingDomainException foreign = assertThrows(ModelingDomainException.class,
                () -> placeEdge(drawn.view(), drawn.serving(), IDS.generate(), drawn.target().id(), List.of(), null,
                        null));

        assertEquals("INV-MDL-008", foreign.code(), "конец не на этом представлении");
    }

    @Test
    @DisplayName("UC-MDL-003: связь Access ложится в указанную подпапку Relations с видом доступа")
    void accessRelationshipLandsInSubfolderWithAccessType() {
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse data = ports.element(ALICE, model, ArchiType.ofSimpleName("DataObject"), "Счёт");
        ModelFolder relations = ports.models.get(model).roots().get(FolderType.RELATIONS);
        FolderResponse sub = ports.tree.createFolder(ALICE, new CreateFolderCommand(model.value(),
                relations.id().value(), "Потоки", null, null, null));

        RelationshipResponse access = createRelationship(ArchiType.ofSimpleName("AccessRelationship"),
                component.id(), data.id(), sub.id(), "READ", null);

        assertEquals(sub.id(), access.folderId());
        assertEquals("READ", access.accessType());
    }

    @Test
    @DisplayName("UC-MDL-003: связь без папки ложится в Relations, направленность у Association сохраняется")
    void associationLandsInRelationsWithDirection() {
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");
        ModelFolder relations = ports.models.get(model).roots().get(FolderType.RELATIONS);

        RelationshipResponse association = createRelationship(ArchiType.ofSimpleName("AssociationRelationship"),
                component.id(), actor.id(), null, null, true);

        assertEquals(relations.id().value(), association.folderId());
        assertEquals(true, association.directed());
    }

    @Test
    @DisplayName("UC-MDL-003: вид доступа у связи не Access — 422")
    void accessTypeOutsideAccessIsRejected() {
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");

        ModelingDomainException wrongAttribute = assertThrows(ModelingDomainException.class,
                () -> createRelationship(SERVING, component.id(), actor.id(), null, "READ", null));

        assertEquals(Failure.UNPROCESSABLE, wrongAttribute.failure());
    }

    @Test
    @DisplayName("UC-MDL-003: неизвестный вид доступа — отказ во вводе")
    void unknownAccessTypeIsRejected() {
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse data = ports.element(ALICE, model, ArchiType.ofSimpleName("DataObject"), "Счёт");

        ModelingDomainException unknownAccess = assertThrows(ModelingDomainException.class,
                () -> createRelationship(ArchiType.ofSimpleName("AccessRelationship"), component.id(), data.id(),
                        null, "SOMETIMES", null));

        assertEquals(ModelingCodes.INVALID_INPUT, unknownAccess.code(), "вид доступа разбирает сценарий");
    }

    @Test
    @DisplayName("INV-MDL-009: связь не кладётся в папку чужого слоя")
    void relationshipOutsideRelationsFolderIsRejected() {
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");
        ModelFolder application = ports.models.get(model).roots().get(FolderType.APPLICATION);

        ModelingDomainException wrongFolder = assertThrows(ModelingDomainException.class,
                () -> createRelationship(SERVING, component.id(), actor.id(), application.id().value(), null,
                        null));

        assertEquals("INV-MDL-009", wrongFolder.code());
    }

    private Placed placed() {
        ElementResponse component = createElement("АБС", IDS.generate(), "id-abs", null);
        ViewSummaryResponse view = ports.view(ALICE, model, "Контекст");
        ViewNodeResponse node = ports.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55));
        return new Placed(component, view, node);
    }

    private Drawn undrawn() {
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");
        UUID serving = ports.relate(ALICE, model, SERVING, component.id(), actor.id()).id();
        ViewSummaryResponse view = ports.view(ALICE, model, "Контекст");
        ViewNodeResponse c = ports.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55));
        ViewNodeResponse a = ports.place(ALICE, view.id(), actor.id(), new Bounds(300, 10, 120, 55));
        return new Drawn(view.id(), serving, c, a, null);
    }

    private Drawn drawn() {
        Drawn drawn = undrawn();
        ViewEdgeResponse edge = placeEdge(drawn.view(), drawn.serving(), drawn.source().id(), drawn.target().id(),
                BEND, null, null).edge();
        return new Drawn(drawn.view(), drawn.serving(), drawn.source(), drawn.target(), edge);
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

    /** Элемент с клиентскими id и его узел на представлении. */
    private record Placed(ElementResponse component, ViewSummaryResponse view, ViewNodeResponse node) {
    }

    /** Связь обслуживания, узлы её концов на представлении и ребро между ними, если оно проведено. */
    private record Drawn(UUID view, UUID serving, ViewNodeResponse source, ViewNodeResponse target,
                         ViewEdgeResponse edge) {
    }
}
