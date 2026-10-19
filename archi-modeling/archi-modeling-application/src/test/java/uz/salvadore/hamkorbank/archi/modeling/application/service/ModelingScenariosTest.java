package uz.salvadore.hamkorbank.archi.modeling.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteTreeItemsCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceAccessListCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListModelsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.SuggestRelationTypesQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.AccessListResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlacementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelVersionCommittedEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.layout.LayoutGraph;

class ModelingScenariosTest {

    private static final EditorIdentity ALICE = EditorIdentity.of("alice", Role.ARCHITECT);
    private static final EditorIdentity BOB = EditorIdentity.of("bob", Role.ARCHITECT);
    private static final EditorIdentity READER = EditorIdentity.of("reader", Role.VIEWER);
    private static final ArchiType COMPONENT = ArchiType.ofSimpleName("ApplicationComponent");

    private final InMemoryPorts ports = new InMemoryPorts();

    @Test
    @DisplayName("UC-MDL-001: модель создаётся с версией 1, повтор ключа — та же модель")
    void createModelIsIdempotentAndStartsHistory() {
        ModelSummaryResponse first = create(ALICE, "Ландшафт", "k-1");
        ModelSummaryResponse replay = create(ALICE, "Ландшафт", "k-1");

        assertEquals(first.id(), replay.id());
        assertEquals(1, ports.models.size());
        assertEquals(1, ports.versionService.list(ALICE, ModelId.of(first.id())).size());
        assertEquals(9, ports.models.get(ModelId.of(first.id())).roots().size());
        ModelingDomainException other = assertThrows(ModelingDomainException.class,
                () -> create(ALICE, "Другая", "k-1"));
        assertEquals("INV-MDL-003", other.code());
    }

    @Test
    @DisplayName("FR-28: VIEWER не создаёт модель и не правит, но читает")
    void viewerReadsButDoesNotWrite() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> create(READER, "x", null)).failure());
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.lock(READER, model)).failure());
        assertEquals("Ландшафт", open(READER, model).model().name());
    }

    @Test
    @DisplayName("FR-28: роль проверяется раньше разбора ввода — читатель получает 403, а не 422")
    void roleIsCheckedBeforeInput() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);
        View view = ports.viewService.create(ALICE, model, "Контекст", null, null);
        ViewService.NewNode noElement = new ViewService.NewNode(ViewNodeKind.DIAGRAM_OBJECT, null, null, null,
                0, 0, null, null, null, RequestedIds.NONE);

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.viewService.placeNode(READER, view.id(), noElement)).failure());
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.viewService.proposeLayout(READER, view.id(), "no-such-strategy",
                        new LayoutGraph(List.of(), List.of()))).failure());
    }

    @Test
    @DisplayName("FR-28: отказ роли называет операцию сообщением, а не именем сценария и списком ролей")
    void roleDenialNamesOperationByKey() {
        ModelingDomainException denied = assertThrows(ModelingDomainException.class,
                () -> create(READER, "x", null));

        assertEquals(List.of(Message.of(ModelingMessages.OPERATION_CREATE_MODEL)), denied.reason().args());
    }

    @Test
    @DisplayName("INV-MDL-006: правка без блокировки — 409, второй редактор — 409 с владельцем")
    void writesRequireOwnLock() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");

        ModelingDomainException noLock = assertThrows(ModelingDomainException.class,
                () -> ports.element(ALICE, model, COMPONENT, "АБС"));
        assertEquals("INV-MDL-006", noLock.code());

        ports.lock(ALICE, model);
        ElementResponse element = ports.element(ALICE, model, COMPONENT, "АБС");
        assertEquals("АБС", element.name());

        ModelingDomainException taken = assertThrows(ModelingDomainException.class,
                () -> ports.lock(BOB, model));
        assertEquals("alice", taken.details().get("lockOwner"));
        assertThrows(ModelingDomainException.class, () -> ports.element(BOB, model, COMPONENT, "CRM"));

        ports.clock.advance(Duration.ofMinutes(31));
        assertEquals("bob", ports.lock(BOB, model).owner(), "просроченная блокировка не держит");
    }

    @Test
    @DisplayName("UC-MDL-004: изменений нет — версия не создаётся; есть — номер растёт")
    void saveCreatesVersionOnlyOnChanges() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);

        VersionService.SaveResult unchanged = ports.versionService.save(ALICE, model, "пусто",
                null);
        assertFalse(unchanged.created());
        assertEquals(1, unchanged.version().versionNo());

        ports.element(ALICE, model, COMPONENT, "АБС");
        VersionService.SaveResult saved = ports.versionService.save(ALICE, model, "АБС",
                "save-1");
        VersionService.SaveResult replay = ports.versionService.save(ALICE, model, "АБС",
                "save-1");

        assertTrue(saved.created());
        assertEquals(2, saved.version().versionNo());
        assertEquals(2, replay.version().versionNo());
        assertEquals(2, ports.versions.size());
        assertEquals(2, ports.published.stream().filter(ModelVersionCommittedEvent.class::isInstance).count());
    }

    @Test
    @DisplayName("INV-MDL-011: модель со списком скрыта от чужих — её нет в списке, прямой запрос 404")
    void accessListHidesModel() {
        ModelId model = ports.createModel(ALICE, "Секретная");
        ports.accessLists.replaceAccessList(ALICE, new ReplaceAccessListCommand(model.value(),
                List.of(acl("USER", "alice", "WRITE"))));

        assertEquals(List.of(), list(BOB));
        assertEquals(Failure.NOT_FOUND, assertThrows(ModelingDomainException.class,
                () -> open(BOB, model)).failure());
        assertEquals(1, list(ALICE).size());
        assertEquals(Failure.NOT_FOUND, assertThrows(ModelingDomainException.class,
                () -> clearAccess(BOB, model)).failure(),
                "скрытая модель отвечает 404 раньше, чем выдала бы себя отказом 403");

        ports.accessLists.replaceAccessList(ALICE, new ReplaceAccessListCommand(model.value(),
                List.of(acl("USER", "alice", "WRITE"), acl("GROUP", "audit", "READ"))));
        EditorIdentity auditor = new EditorIdentity("carol", Set.of(Role.ARCHITECT), Set.of("audit"));
        assertEquals("Секретная", open(auditor, model).model().name());
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> clearAccess(auditor, model)).failure(), "список меняют автор и ADMIN");
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.lock(auditor, model)).failure(), "READ не даёт правки");
    }

    @Test
    @DisplayName("FR-31: дерево модели с размещениями — запись на каждое представление, пустое тоже")
    void treeWithPlacementsCoversEveryView() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        View placed = ports.viewService.create(ALICE, model, "Контекст", null, null);
        View empty = ports.viewService.create(ALICE, model, "Пустое", null, null);
        ports.viewService.place(ALICE, placed.id(), ElementId.of(component.id()), new Bounds(10, 10, 120, 55), null);

        ModelTreeResponse opened = open(READER, model);

        assertEquals(List.of(placed.id().value(), empty.id().value()),
                opened.placements().stream().map(PlacementResponse::viewId).toList());
        assertEquals(List.of(component.id()), opened.placements().getFirst().elementIds());
        assertTrue(opened.placements().getLast().elementIds().isEmpty());
    }

    @Test
    @DisplayName("INV-MDL-004: удаление элемента со связью — 409; групповое вместе со связью проходит")
    void deleteWithRelationshipsTogether() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse actor = ports.element(ALICE, model, ArchiType.ofSimpleName("BusinessActor"), "Клиент");
        RelationshipResponse serving = ports.relate(ALICE, model, ArchiType.ofSimpleName("ServingRelationship"),
                component.id(), actor.id());
        View view = ports.viewService.create(ALICE, model, "Контекст", null, null);
        ports.viewService.place(ALICE, view.id(), ElementId.of(component.id()), new Bounds(10, 10, 120, 55), null);

        ModelingDomainException blocked = assertThrows(ModelingDomainException.class,
                () -> ports.elements.deleteElement(ALICE, new DeleteElementCommand(component.id())));
        assertEquals("INV-MDL-004", blocked.code());

        ports.tree.deleteTreeItems(ALICE, new DeleteTreeItemsCommand(model.value(),
                List.of(component.id(), serving.id())));

        assertEquals(1, ports.models.get(model).elements().size());
        assertEquals(0, ports.views.get(view.id()).nodes().size(), "размещение ушло вместе с элементом");
    }

    @Test
    @DisplayName("UI-004: среди допустимых связей пары помечена подразумеваемая вложением — одна или ни одной")
    void suggestionsMarkNestingRelationship() {
        List<RelationTypeResponse> nodeInNode = suggest("Node", "SystemSoftware");
        assertEquals(List.of("COMPOSITION"), nodeInNode.stream().filter(RelationTypeResponse::nesting)
                .map(RelationTypeResponse::type).toList(), "где допустимы обе — composition");
        assertTrue(nodeInNode.getFirst().byDefault());
        assertEquals(1, nodeInNode.stream().filter(RelationTypeResponse::byDefault).count());

        List<RelationTypeResponse> actorInRole = suggest("BusinessActor", "BusinessRole");
        assertEquals(List.of("ASSIGNMENT"), actorInRole.stream().filter(RelationTypeResponse::nesting)
                .map(RelationTypeResponse::type).toList());

        List<RelationTypeResponse> componentInProcess = suggest("ApplicationComponent", "BusinessProcess");
        assertFalse(componentInProcess.stream().anyMatch(RelationTypeResponse::nesting),
                "вложенность связи не подразумевает");
        assertEquals(ModelingCodes.INVALID_INPUT, assertThrows(ModelingDomainException.class,
                () -> ports.metamodel.suggestRelationTypes(new SuggestRelationTypesQuery(
                        ArchiType.ofSimpleName("Node").value(), "Node"))).code(),
                "тип без префикса нотации — отказ во вводе");
    }

    private List<RelationTypeResponse> suggest(String source, String target) {
        return ports.metamodel.suggestRelationTypes(new SuggestRelationTypesQuery(
                ArchiType.ofSimpleName(source).value(), ArchiType.ofSimpleName(target).value()));
    }

    private ModelSummaryResponse create(EditorIdentity actor, String name, String key) {
        return ports.lifecycle.createModel(actor, new CreateModelCommand(ports.workspace.id().value(), name, key));
    }

    private ModelTreeResponse open(EditorIdentity actor, ModelId model) {
        return ports.queries.getModel(actor, new GetModelQuery(model.value()));
    }

    private List<ModelSummaryResponse> list(EditorIdentity actor) {
        return ports.queries.listModels(actor, new ListModelsQuery(ports.workspace.id().value()));
    }

    private static ReplaceAccessListCommand.Entry acl(String principalType, String principal, String access) {
        return new ReplaceAccessListCommand.Entry(principalType, principal, access);
    }

    private AccessListResponse clearAccess(EditorIdentity actor, ModelId model) {
        return ports.accessLists.replaceAccessList(actor, new ReplaceAccessListCommand(model.value(), List.of()));
    }
}
