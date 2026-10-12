package uz.salvadore.hamkorbank.archi.modeling.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.PrincipalType;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelVersionCommitted;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;

class ModelingScenariosTest {

    private static final EditorIdentity ALICE = EditorIdentity.of("alice", Role.ARCHITECT);
    private static final EditorIdentity BOB = EditorIdentity.of("bob", Role.ARCHITECT);
    private static final EditorIdentity READER = EditorIdentity.of("reader", Role.VIEWER);
    private static final ArchiType COMPONENT = ArchiType.ofSimpleName("ApplicationComponent");

    private final InMemoryPorts ports = new InMemoryPorts();

    @Test
    @DisplayName("UC-MDL-001: модель создаётся с версией 1, повтор ключа — та же модель")
    void createModelIsIdempotentAndStartsHistory() {
        ArchitectureModel first = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", Optional.of("k-1"));
        ArchitectureModel replay = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", Optional.of("k-1"));

        assertEquals(first.id(), replay.id());
        assertEquals(1, ports.models.size());
        assertEquals(1, ports.versionService.list(ALICE, first.id()).size());
        assertEquals(9, ports.models.get(first.id()).roots().size());
        ModelingException other = assertThrows(ModelingException.class,
                () -> ports.lifecycle.create(ALICE, ports.workspace.id(), "Другая", Optional.of("k-1")));
        assertEquals("INV-MDL-003", other.code());
    }

    @Test
    @DisplayName("FR-28: VIEWER не создаёт модель и не правит, но читает")
    void viewerReadsButDoesNotWrite() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", Optional.empty()).id();

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingException.class,
                () -> ports.lifecycle.create(READER, ports.workspace.id(), "x", Optional.empty())).failure());
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingException.class,
                () -> ports.lockService.acquire(READER, model)).failure());
        assertEquals("Ландшафт", ports.queries.open(READER, model).name());
    }

    @Test
    @DisplayName("INV-MDL-006: правка без блокировки — 409, второй редактор — 409 с владельцем")
    void writesRequireOwnLock() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", Optional.empty()).id();

        ModelingException noLock = assertThrows(ModelingException.class, () -> ports.elements.create(ALICE, model,
                COMPONENT, "АБС", Optional.empty(), Optional.empty()));
        assertEquals("INV-MDL-006", noLock.code());

        ports.lockService.acquire(ALICE, model);
        Element element = ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.empty(), Optional.empty());
        assertEquals("АБС", element.name());

        ModelingException taken = assertThrows(ModelingException.class, () -> ports.lockService.acquire(BOB, model));
        assertEquals("alice", taken.details().get("lockOwner"));
        assertThrows(ModelingException.class, () -> ports.elements.create(BOB, model, COMPONENT, "CRM",
                Optional.empty(), Optional.empty()));

        ports.clock.advance(Duration.ofMinutes(31));
        assertEquals("bob", ports.lockService.acquire(BOB, model).owner(), "просроченная блокировка не держит");
    }

    @Test
    @DisplayName("UC-MDL-004: изменений нет — версия не создаётся; есть — номер растёт")
    void saveCreatesVersionOnlyOnChanges() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", Optional.empty()).id();
        ports.lockService.acquire(ALICE, model);

        VersionService.SaveResult unchanged = ports.versionService.save(ALICE, model, Optional.of("пусто"),
                Optional.empty());
        assertFalse(unchanged.created());
        assertEquals(1, unchanged.version().versionNo());

        ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.empty(), Optional.empty());
        VersionService.SaveResult saved = ports.versionService.save(ALICE, model, Optional.of("АБС"),
                Optional.of("save-1"));
        VersionService.SaveResult replay = ports.versionService.save(ALICE, model, Optional.of("АБС"),
                Optional.of("save-1"));

        assertTrue(saved.created());
        assertEquals(2, saved.version().versionNo());
        assertEquals(2, replay.version().versionNo());
        assertEquals(2, ports.versions.size());
        assertEquals(2, ports.published.stream().filter(ModelVersionCommitted.class::isInstance).count());
    }

    @Test
    @DisplayName("INV-MDL-011: модель со списком скрыта от чужих — её нет в списке, прямой запрос 404")
    void accessListHidesModel() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Секретная", Optional.empty()).id();
        ports.accessLists.replace(ALICE, model, List.of(new AclEntry(PrincipalType.USER, "alice", AclAccess.WRITE)));

        assertEquals(List.of(), ports.queries.list(BOB, ports.workspace.id()));
        assertEquals(Failure.NOT_FOUND, assertThrows(ModelingException.class,
                () -> ports.queries.open(BOB, model)).failure());
        assertEquals(1, ports.queries.list(ALICE, ports.workspace.id()).size());
        assertEquals(Failure.NOT_FOUND, assertThrows(ModelingException.class,
                () -> ports.accessLists.replace(BOB, model, List.of())).failure(),
                "скрытая модель отвечает 404 раньше, чем выдала бы себя отказом 403");

        ports.accessLists.replace(ALICE, model, List.of(new AclEntry(PrincipalType.USER, "alice", AclAccess.WRITE),
                new AclEntry(PrincipalType.GROUP, "audit", AclAccess.READ)));
        EditorIdentity auditor = new EditorIdentity("carol", Set.of(Role.ARCHITECT), Set.of("audit"));
        assertEquals("Секретная", ports.queries.open(auditor, model).name());
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingException.class,
                () -> ports.accessLists.replace(auditor, model, List.of())).failure(), "список меняют автор и ADMIN");
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingException.class,
                () -> ports.lockService.acquire(auditor, model)).failure(), "READ не даёт правки");
    }

    @Test
    @DisplayName("FR-31: дерево модели с размещениями — запись на каждое представление, пустое тоже")
    void treeWithPlacementsCoversEveryView() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", Optional.empty()).id();
        ports.lockService.acquire(ALICE, model);
        Element component = ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.empty(), Optional.empty());
        View placed = ports.viewService.create(ALICE, model, "Контекст", Optional.empty(), Optional.empty());
        View empty = ports.viewService.create(ALICE, model, "Пустое", Optional.empty(), Optional.empty());
        ports.viewService.place(ALICE, placed.id(), component.id(), new Bounds(10, 10, 120, 55), Optional.empty());

        var opened = ports.queries.openWithPlacements(READER, model);

        assertEquals(List.of(placed.id(), empty.id()), opened.placements().stream().map(p -> p.viewId()).toList());
        assertEquals(List.of(component.id()), opened.placements().getFirst().elementIds());
        assertTrue(opened.placements().getLast().elementIds().isEmpty());
    }

    @Test
    @DisplayName("INV-MDL-004: удаление элемента со связью — 409; групповое вместе со связью проходит")
    void deleteWithRelationshipsTogether() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", Optional.empty()).id();
        ports.lockService.acquire(ALICE, model);
        Element component = ports.elements.create(ALICE, model, COMPONENT, "АБС", Optional.empty(), Optional.empty());
        Element actor = ports.elements.create(ALICE, model, ArchiType.ofSimpleName("BusinessActor"), "Клиент",
                Optional.empty(), Optional.empty());
        var serving = ports.relationships.create(ALICE, model, ArchiType.ofSimpleName("ServingRelationship"),
                component.id().value(), actor.id().value(), Optional.empty(), Optional.empty(), Optional.empty());
        View view = ports.viewService.create(ALICE, model, "Контекст", Optional.empty(), Optional.empty());
        ports.viewService.place(ALICE, view.id(), component.id(), new Bounds(10, 10, 120, 55), Optional.empty());

        ModelingException blocked = assertThrows(ModelingException.class,
                () -> ports.elements.delete(ALICE, component.id()));
        assertEquals("INV-MDL-004", blocked.code());

        ports.tree.delete(ALICE, model, List.of(component.id().value(), serving.relationship().id().value()));

        assertEquals(1, ports.models.get(model).elements().size());
        assertEquals(0, ports.views.get(view.id()).nodes().size(), "размещение ушло вместе с элементом");
    }
}
