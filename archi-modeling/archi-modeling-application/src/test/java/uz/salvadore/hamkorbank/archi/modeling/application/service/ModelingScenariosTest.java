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
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceAccessListCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.AccessListResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelVersionCommittedEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
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
        ArchitectureModel first = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", "k-1");
        ArchitectureModel replay = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", "k-1");

        assertEquals(first.id(), replay.id());
        assertEquals(1, ports.models.size());
        assertEquals(1, ports.versionService.list(ALICE, first.id()).size());
        assertEquals(9, ports.models.get(first.id()).roots().size());
        ModelingDomainException other = assertThrows(ModelingDomainException.class,
                () -> ports.lifecycle.create(ALICE, ports.workspace.id(), "Другая", "k-1"));
        assertEquals("INV-MDL-003", other.code());
    }

    @Test
    @DisplayName("FR-28: VIEWER не создаёт модель и не правит, но читает")
    void viewerReadsButDoesNotWrite() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", null).id();

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.lifecycle.create(READER, ports.workspace.id(), "x", null)).failure());
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.lock(READER, model)).failure());
        assertEquals("Ландшафт", ports.queries.open(READER, model).name());
    }

    @Test
    @DisplayName("FR-28: роль проверяется раньше разбора ввода — читатель получает 403, а не 422")
    void roleIsCheckedBeforeInput() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", null).id();
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
                () -> ports.lifecycle.create(READER, ports.workspace.id(), "x", null));

        assertEquals(List.of(Message.of(ModelingMessages.OPERATION_CREATE_MODEL)), denied.reason().args());
    }

    @Test
    @DisplayName("INV-MDL-006: правка без блокировки — 409, второй редактор — 409 с владельцем")
    void writesRequireOwnLock() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", null).id();

        ModelingDomainException noLock = assertThrows(ModelingDomainException.class,
                () -> ports.elements.create(ALICE, model,
                COMPONENT, "АБС", null, null));
        assertEquals("INV-MDL-006", noLock.code());

        ports.lock(ALICE, model);
        Element element = ports.elements.create(ALICE, model, COMPONENT, "АБС", null, null);
        assertEquals("АБС", element.name());

        ModelingDomainException taken = assertThrows(ModelingDomainException.class,
                () -> ports.lock(BOB, model));
        assertEquals("alice", taken.details().get("lockOwner"));
        assertThrows(ModelingDomainException.class, () -> ports.elements.create(BOB, model, COMPONENT, "CRM",
                null, null));

        ports.clock.advance(Duration.ofMinutes(31));
        assertEquals("bob", ports.lock(BOB, model).owner(), "просроченная блокировка не держит");
    }

    @Test
    @DisplayName("UC-MDL-004: изменений нет — версия не создаётся; есть — номер растёт")
    void saveCreatesVersionOnlyOnChanges() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", null).id();
        ports.lock(ALICE, model);

        VersionService.SaveResult unchanged = ports.versionService.save(ALICE, model, "пусто",
                null);
        assertFalse(unchanged.created());
        assertEquals(1, unchanged.version().versionNo());

        ports.elements.create(ALICE, model, COMPONENT, "АБС", null, null);
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
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Секретная", null).id();
        ports.accessLists.replaceAccessList(ALICE, new ReplaceAccessListCommand(model.value(),
                List.of(acl("USER", "alice", "WRITE"))));

        assertEquals(List.of(), ports.queries.list(BOB, ports.workspace.id()));
        assertEquals(Failure.NOT_FOUND, assertThrows(ModelingDomainException.class,
                () -> ports.queries.open(BOB, model)).failure());
        assertEquals(1, ports.queries.list(ALICE, ports.workspace.id()).size());
        assertEquals(Failure.NOT_FOUND, assertThrows(ModelingDomainException.class,
                () -> clearAccess(BOB, model)).failure(),
                "скрытая модель отвечает 404 раньше, чем выдала бы себя отказом 403");

        ports.accessLists.replaceAccessList(ALICE, new ReplaceAccessListCommand(model.value(),
                List.of(acl("USER", "alice", "WRITE"), acl("GROUP", "audit", "READ"))));
        EditorIdentity auditor = new EditorIdentity("carol", Set.of(Role.ARCHITECT), Set.of("audit"));
        assertEquals("Секретная", ports.queries.open(auditor, model).name());
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> clearAccess(auditor, model)).failure(), "список меняют автор и ADMIN");
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ports.lock(auditor, model)).failure(), "READ не даёт правки");
    }

    @Test
    @DisplayName("FR-31: дерево модели с размещениями — запись на каждое представление, пустое тоже")
    void treeWithPlacementsCoversEveryView() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", null).id();
        ports.lock(ALICE, model);
        Element component = ports.elements.create(ALICE, model, COMPONENT, "АБС", null, null);
        View placed = ports.viewService.create(ALICE, model, "Контекст", null, null);
        View empty = ports.viewService.create(ALICE, model, "Пустое", null, null);
        ports.viewService.place(ALICE, placed.id(), component.id(), new Bounds(10, 10, 120, 55), null);

        ModelQueryService.OpenedModel opened = ports.queries.openWithPlacements(READER, model);

        assertEquals(List.of(placed.id(), empty.id()), opened.placements().stream().map(p -> p.viewId()).toList());
        assertEquals(List.of(component.id()), opened.placements().getFirst().elementIds());
        assertTrue(opened.placements().getLast().elementIds().isEmpty());
    }

    @Test
    @DisplayName("INV-MDL-004: удаление элемента со связью — 409; групповое вместе со связью проходит")
    void deleteWithRelationshipsTogether() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", null).id();
        ports.lock(ALICE, model);
        Element component = ports.elements.create(ALICE, model, COMPONENT, "АБС", null, null);
        Element actor = ports.elements.create(ALICE, model, ArchiType.ofSimpleName("BusinessActor"), "Клиент",
                null, null);
        RelationshipService.Result serving = ports.relationships.create(ALICE, model,
                ArchiType.ofSimpleName("ServingRelationship"),
                component.id().value(), actor.id().value(), (String) null, null, null);
        View view = ports.viewService.create(ALICE, model, "Контекст", null, null);
        ports.viewService.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55), null);

        ModelingDomainException blocked = assertThrows(ModelingDomainException.class,
                () -> ports.elements.delete(ALICE, component.id()));
        assertEquals("INV-MDL-004", blocked.code());

        ports.tree.delete(ALICE, model, List.of(component.id().value(), serving.relationship().id().value()));

        assertEquals(1, ports.models.get(model).elements().size());
        assertEquals(0, ports.views.get(view.id()).nodes().size(), "размещение ушло вместе с элементом");
    }

    @Test
    @DisplayName("UI-004: среди допустимых связей пары помечена подразумеваемая вложением — одна или ни одной")
    void suggestionsMarkNestingRelationship() {
        List<RelationshipService.Suggestion> nodeInNode =
                RelationshipService.suggestions(ArchiType.ofSimpleName("Node"),
                ArchiType.ofSimpleName("SystemSoftware"));
        assertEquals(List.of("COMPOSITION"), nodeInNode.stream().filter(RelationshipService.Suggestion::nesting)
                .map(x -> x.type().name()).toList(), "где допустимы обе — composition");
        assertTrue(nodeInNode.getFirst().byDefault());
        assertEquals(1, nodeInNode.stream().filter(RelationshipService.Suggestion::byDefault).count());

        List<RelationshipService.Suggestion> actorInRole =
                RelationshipService.suggestions(ArchiType.ofSimpleName("BusinessActor"),
                ArchiType.ofSimpleName("BusinessRole"));
        assertEquals(List.of("ASSIGNMENT"), actorInRole.stream().filter(RelationshipService.Suggestion::nesting)
                .map(x -> x.type().name()).toList());

        List<RelationshipService.Suggestion> componentInProcess =
                RelationshipService.suggestions(COMPONENT, ArchiType.ofSimpleName("BusinessProcess"));
        assertFalse(componentInProcess.stream().anyMatch(RelationshipService.Suggestion::nesting),
                "вложенность связи не подразумевает");
    }

    private static ReplaceAccessListCommand.Entry acl(String principalType, String principal, String access) {
        return new ReplaceAccessListCommand.Entry(principalType, principal, access);
    }

    private AccessListResponse clearAccess(EditorIdentity actor, ModelId model) {
        return ports.accessLists.replaceAccessList(actor, new ReplaceAccessListCommand(model.value(), List.of()));
    }
}
