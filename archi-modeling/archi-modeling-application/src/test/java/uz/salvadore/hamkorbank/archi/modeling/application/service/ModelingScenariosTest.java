package uz.salvadore.hamkorbank.archi.modeling.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteTreeItemsCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceAccessListCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListModelsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListVersionsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ProposeViewLayoutQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.SuggestRelationTypesQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.AccessListResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlacementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.SaveVersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelVersionCommittedEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

class ModelingScenariosTest {

    private static final EditorIdentity ALICE = EditorIdentity.of("alice", Role.ARCHITECT);
    private static final EditorIdentity BOB = EditorIdentity.of("bob", Role.ARCHITECT);
    private static final EditorIdentity READER = EditorIdentity.of("reader", Role.VIEWER);
    private static final EditorIdentity AUDITOR =
            new EditorIdentity("carol", Set.of(Role.ARCHITECT), Set.of("audit"));
    private static final ArchiType COMPONENT = ArchiType.ofSimpleName("ApplicationComponent");

    private final InMemoryPorts ports = new InMemoryPorts();

    @Test
    @DisplayName("UC-MDL-001: модель создаётся с версией 1 и корневыми папками")
    void createModelStartsHistory() {
        ModelSummaryResponse first = create(ALICE, "Ландшафт", "k-1");

        assertEquals(1, ports.models.size());
        assertEquals(1, ports.versionService.listVersions(ALICE, new ListVersionsQuery(first.id(), null, null)).size());
        assertEquals(9, ports.models.get(ModelId.of(first.id())).roots().size());
    }

    @Test
    @DisplayName("UC-MDL-001: повтор ключа — та же модель")
    void createModelIsIdempotent() {
        ModelSummaryResponse first = create(ALICE, "Ландшафт", "k-1");

        ModelSummaryResponse replay = create(ALICE, "Ландшафт", "k-1");

        assertEquals(first.id(), replay.id());
        assertEquals(1, ports.models.size());
        assertEquals(1, ports.versionService.listVersions(ALICE, new ListVersionsQuery(first.id(), null, null)).size());
    }

    @Test
    @DisplayName("INV-MDL-003: тот же ключ с другим телом команды — отказ")
    void sameKeyWithOtherBodyIsRejected() {
        create(ALICE, "Ландшафт", "k-1");

        ModelingDomainException other = assertThrows(ModelingDomainException.class,
                () -> create(ALICE, "Другая", "k-1"));

        assertEquals("INV-MDL-003", other.code());
    }

    @Test
    @DisplayName("FR-28: VIEWER не создаёт модель")
    void viewerDoesNotCreateModel() {
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> create(READER, "x", null)).failure());
    }

    @Test
    @DisplayName("FR-28: VIEWER не берёт блокировку на правку")
    void viewerDoesNotLock() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.lock(READER, model)).failure());
    }

    @Test
    @DisplayName("FR-28: VIEWER читает модель")
    void viewerReadsModel() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");

        assertEquals("Ландшафт", open(READER, model).model().name());
    }

    @Test
    @DisplayName("FR-28: роль проверяется раньше разбора ввода — читатель получает 403 на размещение, а не 422")
    void roleIsCheckedBeforeInputOnPlacement() {
        ViewSummaryResponse view = lockedView();

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.viewService.placeNode(READER, objectWithoutElement(view))).failure());
    }

    @Test
    @DisplayName("FR-28: роль проверяется раньше разбора ввода — читатель получает 403 на разметку, а не 422")
    void roleIsCheckedBeforeInputOnLayout() {
        ViewSummaryResponse view = lockedView();

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.viewQueries.proposeLayout(READER, new ProposeViewLayoutQuery(view.id(),
                        "no-such-strategy", List.of(), List.of()))).failure());
    }

    @Test
    @DisplayName("FR-28: автору с ролью неразборчивый ввод — отказ во вводе")
    void authorGetsInputRefusal() {
        ViewSummaryResponse view = lockedView();

        assertEquals(ModelingCodes.INVALID_INPUT, assertThrows(ModelingDomainException.class,
                () -> ports.viewService.placeNode(ALICE, objectWithoutElement(view))).code(),
                "автору — отказ во вводе");
    }

    @Test
    @DisplayName("FR-28: отказ роли называет операцию сообщением, а не именем сценария и списком ролей")
    void roleDenialNamesOperationByKey() {
        ModelingDomainException denied = assertThrows(ModelingDomainException.class,
                () -> create(READER, "x", null));

        assertEquals(List.of(Message.of(ModelingMessages.OPERATION_CREATE_MODEL)), denied.reason().args());
    }

    @Test
    @DisplayName("INV-MDL-006: правка без блокировки — 409")
    void writeWithoutLockIsRejected() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");

        ModelingDomainException noLock = assertThrows(ModelingDomainException.class,
                () -> ports.element(ALICE, model, COMPONENT, "АБС"));

        assertEquals("INV-MDL-006", noLock.code());
    }

    @Test
    @DisplayName("INV-MDL-006: правка под своей блокировкой проходит")
    void writeUnderOwnLockSucceeds() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);

        ElementResponse element = ports.element(ALICE, model, COMPONENT, "АБС");

        assertEquals("АБС", element.name());
    }

    @Test
    @DisplayName("INV-MDL-006: второй редактор блокировку не берёт — 409 с владельцем")
    void secondEditorSeesLockOwner() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);

        ModelingDomainException taken = assertThrows(ModelingDomainException.class,
                () -> ports.lock(BOB, model));

        assertEquals("alice", taken.details().get("lockOwner"));
    }

    @Test
    @DisplayName("INV-MDL-006: второй редактор под чужой блокировкой не правит")
    void secondEditorCannotWrite() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);

        assertThrows(ModelingDomainException.class, () -> ports.element(BOB, model, COMPONENT, "CRM"));
    }

    @Test
    @DisplayName("INV-MDL-006: просроченная блокировка не держит — её берёт другой редактор")
    void expiredLockIsTakenByOtherEditor() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);

        ports.clock.advance(Duration.ofMinutes(31));

        assertEquals("bob", ports.lock(BOB, model).owner(), "просроченная блокировка не держит");
    }

    @Test
    @DisplayName("UC-MDL-004: изменений нет — версия не создаётся")
    void saveWithoutChangesCreatesNoVersion() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);

        SaveVersionResponse unchanged = ports.versionService.saveVersion(ALICE,
                new SaveVersionCommand(model.value(), "пусто", null));

        assertFalse(unchanged.created());
        assertEquals(1, unchanged.version().versionNo());
    }

    @Test
    @DisplayName("UC-MDL-004: изменения есть — номер растёт, повтор ключа отдаёт ту же версию")
    void saveWithChangesCreatesVersion() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);
        ports.element(ALICE, model, COMPONENT, "АБС");

        SaveVersionResponse saved = ports.versionService.saveVersion(ALICE,
                new SaveVersionCommand(model.value(), "АБС", "save-1"));
        SaveVersionResponse replay = ports.versionService.saveVersion(ALICE,
                new SaveVersionCommand(model.value(), "АБС", "save-1"));

        assertTrue(saved.created());
        assertEquals(2, saved.version().versionNo());
        assertEquals(2, replay.version().versionNo());
        assertEquals(2, ports.versions.size());
        assertEquals(2, ports.published.stream().filter(ModelVersionCommittedEvent.class::isInstance).count());
    }

    @Test
    @DisplayName("INV-MDL-011: модель со списком скрыта из списка моделей чужого, автор её видит")
    void accessListHidesModelFromList() {
        secretModel();

        assertEquals(List.of(), list(BOB));
        assertEquals(1, list(ALICE).size());
    }

    @Test
    @DisplayName("INV-MDL-011: прямой запрос чужого к модели со списком — 404")
    void accessListHidesModelFromDirectRequest() {
        ModelId model = secretModel();

        assertEquals(Failure.NOT_FOUND, assertThrows(ModelingDomainException.class,
                () -> open(BOB, model)).failure());
    }

    @Test
    @DisplayName("INV-MDL-011: смена списка чужим — 404 раньше, чем 403 выдал бы модель")
    void accessListHidesModelFromManagement() {
        ModelId model = secretModel();

        assertEquals(Failure.NOT_FOUND, assertThrows(ModelingDomainException.class,
                () -> clearAccess(BOB, model)).failure(),
                "скрытая модель отвечает 404 раньше, чем выдала бы себя отказом 403");
    }

    @Test
    @DisplayName("INV-MDL-011: член группы с доступом READ читает модель")
    void groupReaderOpensModel() {
        ModelId model = auditedModel();

        assertEquals("Секретная", open(AUDITOR, model).model().name());
    }

    @Test
    @DisplayName("INV-MDL-011: член группы с доступом READ список не меняет — 403")
    void groupReaderDoesNotManageList() {
        ModelId model = auditedModel();

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> clearAccess(AUDITOR, model)).failure(), "список меняют автор и ADMIN");
    }

    @Test
    @DisplayName("INV-MDL-011: доступ READ не даёт блокировки на правку — 403")
    void groupReaderDoesNotLock() {
        ModelId model = auditedModel();

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.lock(AUDITOR, model)).failure(), "READ не даёт правки");
    }

    @Test
    @DisplayName("FR-31: дерево модели с размещениями — запись на каждое представление, пустое тоже")
    void treeWithPlacementsCoversEveryView() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ViewSummaryResponse placed = ports.view(ALICE, model, "Контекст");
        ViewSummaryResponse empty = ports.view(ALICE, model, "Пустое");
        ports.place(ALICE, placed.id(), component.id(), new Bounds(10, 10, 120, 55));

        ModelTreeResponse opened = open(READER, model);

        assertEquals(List.of(placed.id(), empty.id()),
                opened.placements().stream().map(PlacementResponse::viewId).toList());
        assertEquals(List.of(component.id()), opened.placements().getFirst().elementIds());
        assertTrue(opened.placements().getLast().elementIds().isEmpty());
    }

    @Test
    @DisplayName("INV-MDL-004: удаление элемента со связью — 409")
    void deleteElementWithRelationshipIsRejected() {
        Served served = served();

        ModelingDomainException blocked = assertThrows(ModelingDomainException.class,
                () -> ports.elements.deleteElement(ALICE, new DeleteElementCommand(served.component())));

        assertEquals("INV-MDL-004", blocked.code());
    }

    @Test
    @DisplayName("INV-MDL-004: групповое удаление элемента вместе со связью проходит")
    void deleteWithRelationshipsTogether() {
        Served served = served();

        ports.tree.deleteTreeItems(ALICE, new DeleteTreeItemsCommand(served.model().value(),
                List.of(served.component(), served.serving())));

        assertEquals(1, ports.models.get(served.model()).elements().size());
        assertEquals(0, ports.views.get(ViewId.of(served.view())).nodes().size(),
                "размещение ушло вместе с элементом");
    }

    @Test
    @DisplayName("UI-004: где допустимы и композиция, и агрегация, вложение подразумевает композицию")
    void nestingPrefersComposition() {
        List<RelationTypeResponse> nodeInNode = suggest("Node", "SystemSoftware");

        assertEquals(List.of("COMPOSITION"), nodeInNode.stream().filter(RelationTypeResponse::nesting)
                .map(RelationTypeResponse::type).toList(), "где допустимы обе — composition");
        assertTrue(nodeInNode.getFirst().byDefault());
        assertEquals(1, nodeInNode.stream().filter(RelationTypeResponse::byDefault).count());
    }

    @Test
    @DisplayName("UI-004: актёр в роли — вложение подразумевает назначение")
    void nestingOfActorInRoleIsAssignment() {
        List<RelationTypeResponse> actorInRole = suggest("BusinessActor", "BusinessRole");

        assertEquals(List.of("ASSIGNMENT"), actorInRole.stream().filter(RelationTypeResponse::nesting)
                .map(RelationTypeResponse::type).toList());
    }

    @Test
    @DisplayName("UI-004: пара, где вложение связи не подразумевает, — ни одной помеченной")
    void nestingImpliesNoRelationship() {
        List<RelationTypeResponse> componentInProcess = suggest("ApplicationComponent", "BusinessProcess");

        assertFalse(componentInProcess.stream().anyMatch(RelationTypeResponse::nesting),
                "вложенность связи не подразумевает");
    }

    @Test
    @DisplayName("UI-004: тип без префикса нотации — отказ во вводе")
    void typeWithoutNotationPrefixIsRejected() {
        assertEquals(ModelingCodes.INVALID_INPUT, assertThrows(ModelingDomainException.class,
                () -> ports.metamodel.suggestRelationTypes(new SuggestRelationTypesQuery(
                        ArchiType.ofSimpleName("Node").value(), "Node"))).code(),
                "тип без префикса нотации — отказ во вводе");
    }

    private ViewSummaryResponse lockedView() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);
        return ports.view(ALICE, model, "Контекст");
    }

    private static PlaceViewNodeCommand objectWithoutElement(ViewSummaryResponse view) {
        return new PlaceViewNodeCommand(view.id(), "DIAGRAM_OBJECT", null, null, null,
                0, 0, null, null, null, null, null);
    }

    private ModelId secretModel() {
        ModelId model = ports.createModel(ALICE, "Секретная");
        ports.accessLists.replaceAccessList(ALICE, new ReplaceAccessListCommand(model.value(),
                List.of(acl("USER", "alice", "WRITE"))));
        return model;
    }

    private ModelId auditedModel() {
        ModelId model = ports.createModel(ALICE, "Секретная");
        ports.accessLists.replaceAccessList(ALICE, new ReplaceAccessListCommand(model.value(),
                List.of(acl("USER", "alice", "WRITE"), acl("GROUP", "audit", "READ"))));
        return model;
    }

    private Served served() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);
        ElementResponse component = ports.element(ALICE, model, COMPONENT, "АБС");
        ElementResponse actor = ports.element(ALICE, model, ArchiType.ofSimpleName("BusinessActor"), "Клиент");
        RelationshipResponse serving = ports.relate(ALICE, model, ArchiType.ofSimpleName("ServingRelationship"),
                component.id(), actor.id());
        ViewSummaryResponse view = ports.view(ALICE, model, "Контекст");
        ports.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55));
        return new Served(model, component.id(), serving.id(), view.id());
    }

    /** Модель с компонентом, связью обслуживания от него и размещением компонента на представлении. */
    private record Served(ModelId model, UUID component, UUID serving, UUID view) {
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
