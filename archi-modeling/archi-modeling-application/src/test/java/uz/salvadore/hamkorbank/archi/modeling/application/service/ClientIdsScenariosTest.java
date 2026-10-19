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
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateFolderCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CreateRelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.FolderResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;

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
        View view = ports.viewService.create(ALICE, model, "Контекст", null,
                RequestedIds.of(viewId, "id-view-1"), null);
        ViewNode node = ports.viewService.place(ALICE, view.id(), ElementId.of(component.id()),
                new Bounds(10, 10, 120, 55), null, RequestedIds.of(nodeId, "id-node-1")).value();
        ViewNode actorNode = ports.viewService.place(ALICE, view.id(), ElementId.of(actor.id()),
                new Bounds(300, 10, 120, 55), null);
        CreateRelationshipResponse serving = ports.relationships.createRelationship(ALICE,
                new CreateRelationshipCommand(model.value(), SERVING.value(), elementId, actor.id(), null, null, null,
                        null, relationshipId, "id-serving",
                        new CreateRelationshipCommand.Edge(view.id().value(), nodeId, actorNode.id().value(), edgeId,
                                "id-edge-1"),
                        null));

        assertEquals(folderId, folder.id());
        assertEquals("id-folder-1", folder.archiId());
        assertEquals(elementId, component.id());
        assertEquals("id-abs", component.archiId());
        assertEquals(viewId, view.id().value());
        assertEquals("id-view-1", view.archiId().value());
        assertEquals(nodeId, node.id().value());
        assertEquals("id-node-1", node.archiId().value());
        assertEquals(relationshipId, serving.relationship().id());
        assertEquals("id-serving", serving.relationship().archiId());
        assertEquals(edgeId, serving.edgeId());
        assertEquals("id-edge-1", ports.views.get(view.id()).requireEdge(ViewEdgeId.of(serving.edgeId()))
                .archiId().value());
    }

    @Test
    @DisplayName("ADR-0018: занятый id или archiId — 409 MDL_ID_TAKEN, недопустимый archiId — 422")
    void takenIdsAreRejected() {
        ElementResponse component = createElement("АБС", IDS.generate(), "id-abs", null);
        View view = ports.viewService.create(ALICE, model, "Контекст", null, null);
        ViewNode node = ports.viewService.place(ALICE, view.id(), ElementId.of(component.id()),
                new Bounds(10, 10, 120, 55), null);

        ModelingDomainException sameId = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", component.id(), null, null));
        assertEquals("MDL_ID_TAKEN", sameId.code());
        assertEquals(Failure.CONFLICT, sameId.failure());

        ModelingDomainException idOfNode = assertThrows(ModelingDomainException.class,
                () -> ports.viewService.create(ALICE,
                model, "Другое", null, RequestedIds.of(node.id().value(), null), null));
        assertEquals("MDL_ID_TAKEN", idOfNode.code(), "ключ узла тоже занят");

        ModelingDomainException sameArchiId = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", null, "id-abs", null));
        assertEquals("MDL_ID_TAKEN", sameArchiId.code());

        ModelingDomainException archiIdOfNode = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", null, node.archiId().value(), null));
        assertEquals("MDL_ID_TAKEN", archiIdOfNode.code(), "archiId узла на представлении — тоже занят в файле");

        ModelingDomainException invalid = assertThrows(ModelingDomainException.class,
                () -> createElement("CRM", null, "не id", null));
        assertEquals(ModelingCodes.INVALID_INPUT, invalid.code());
        assertEquals(Failure.UNPROCESSABLE, invalid.failure());
        assertEquals(1, ports.models.get(model).elements().size(), "отказ ничего не создал");
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
        RelationshipId serving = RelationshipId.of(
                ports.relate(ALICE, model, SERVING, component.id(), actor.id()).id());
        View view = ports.viewService.create(ALICE, model, "Контекст", null, null);
        ViewNode c = ports.viewService.place(ALICE, view.id(), ElementId.of(component.id()),
                new Bounds(10, 10, 120, 55), null);
        ViewNode a = ports.viewService.place(ALICE, view.id(), ElementId.of(actor.id()),
                new Bounds(300, 10, 120, 55), null);
        List<Bendpoint> bend = List.of(new Bendpoint(10, 40, -150, 40));
        ViewEdge edge = ports.viewService.placeEdge(ALICE, view.id(), serving, c.id().value(), a.id().value(),
                bend, RequestedIds.NONE).value();

        ports.viewService.removeNode(ALICE, c.id());
        assertTrue(ports.views.get(view.id()).edges().values().isEmpty(), "ребро ушло вместе с узлом");

        ViewNode restored = ports.viewService.place(ALICE, view.id(), ElementId.of(component.id()), c.bounds(), null,
                RequestedIds.of(c.id().value(), c.archiId().value())).value();
        ArchitectureModel.Created<ViewEdge> again = ports.viewService.placeEdge(ALICE, view.id(), serving,
                restored.id().value(),
                a.id().value(), edge.bendpoints(), RequestedIds.of(edge.id().value(), edge.archiId().value()));

        assertTrue(again.created());
        assertEquals(c.id(), restored.id());
        assertEquals(edge.id(), again.value().id());
        assertEquals(edge.archiId(), again.value().archiId());
        assertEquals(bend, again.value().bendpoints());
        assertEquals(restored.id(), again.value().source(), "ребро лежит в содержимом узла-источника");

        ArchitectureModel.Created<ViewEdge> duplicate = ports.viewService.placeEdge(ALICE, view.id(), serving,
                restored.id().value(),
                a.id().value(), List.of(), RequestedIds.NONE);
        assertFalse(duplicate.created(), "то же ребро между теми же концами не дублируется");
        assertEquals(edge.id(), duplicate.value().id());
    }

    @Test
    @DisplayName("INV-MDL-008: концы ребра не изображают концы связи — 422")
    void edgeEndsMustDepictRelationshipEnds() {
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse actor = ports.element(ALICE, model, ACTOR, "Клиент");
        RelationshipId serving = RelationshipId.of(
                ports.relate(ALICE, model, SERVING, component.id(), actor.id()).id());
        View view = ports.viewService.create(ALICE, model, "Контекст", null, null);
        ViewNode c = ports.viewService.place(ALICE, view.id(), ElementId.of(component.id()),
                new Bounds(10, 10, 120, 55), null);
        ViewNode a = ports.viewService.place(ALICE, view.id(), ElementId.of(actor.id()),
                new Bounds(300, 10, 120, 55), null);

        ModelingDomainException reversed = assertThrows(ModelingDomainException.class,
                () -> ports.viewService.placeEdge(ALICE,
                view.id(), serving, a.id().value(), c.id().value(), List.of(), RequestedIds.NONE));
        assertEquals("INV-MDL-008", reversed.code());
        assertEquals(Failure.UNPROCESSABLE, reversed.failure());

        ModelingDomainException foreign = assertThrows(ModelingDomainException.class,
                () -> ports.viewService.placeEdge(ALICE,
                view.id(), serving, IDS.generate(), a.id().value(), List.of(), RequestedIds.NONE));
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
}
