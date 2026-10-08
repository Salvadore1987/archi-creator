package uz.salvadore.hamkorbank.archi.bootstrap.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ElementService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.LockService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelLifecycleService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.RelationshipService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ViewService;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;

class ModelPersistenceIT extends IntegrationTest {

    @Autowired
    ModelLifecycleService lifecycle;
    @Autowired
    LockService locks;
    @Autowired
    ElementService elements;
    @Autowired
    RelationshipService relationships;
    @Autowired
    ViewService views;
    @Autowired
    ModelQueryService queries;

    @Test
    @DisplayName("INV-MDL-001: дубль archi_id в модели отклоняет база — UNIQUE (model_id, archi_id)")
    void duplicateArchiIdViolatesUniqueConstraint() {
        ModelId model = lifecycle.create(ARCHITECT, workspace(), "Уникальность", Optional.empty()).id();
        locks.acquire(ARCHITECT, model);
        Element element = elements.create(ARCHITECT, model, ArchiType.ofSimpleName("ApplicationComponent"), "АБС",
                Optional.empty(), Optional.empty());
        UUID folder = jdbc.queryForObject("select folder_id from element where id = ?", UUID.class,
                element.id().value());

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into element (id, model_id, folder_id, archi_id, archi_type, layer, name, sort_order, supported)
                values (?, ?, ?, ?, 'archimate:ApplicationComponent', 'APPLICATION', 'Двойник', 9000, true)
                """, UUID.randomUUID(), model.value(), folder, element.archiId().value()));
    }

    @Test
    @DisplayName("§4.3: дерево и представление переживают запись и чтение — поля, порядок, геометрия")
    void modelAndViewSurviveStorage() {
        ModelId model = lifecycle.create(ARCHITECT, workspace(), "Хранение", Optional.of("persist-1")).id();
        locks.acquire(ARCHITECT, model);
        Element component = elements.create(ARCHITECT, model, ArchiType.ofSimpleName("ApplicationComponent"), "АБС",
                Optional.empty(), Optional.empty());
        Element actor = elements.create(ARCHITECT, model, ArchiType.ofSimpleName("BusinessActor"), "Клиент",
                Optional.empty(), Optional.empty());
        elements.update(ARCHITECT, component.id(), Optional.empty(), Optional.of("Ядро банка"),
                Optional.of(List.of(new ElementService.PropertyValue("owner", "ИТ"),
                        new ElementService.PropertyValue("criticality", "high"))));
        View view = views.create(ARCHITECT, model, "Контекст", Optional.empty(), Optional.empty());
        ViewNode c = views.place(ARCHITECT, view.id(), component.id(), new Bounds(20, 60, 150, 60), Optional.empty());
        ViewNode a = views.place(ARCHITECT, view.id(), actor.id(), new Bounds(300, 60, 150, 60), Optional.empty());
        var serving = relationships.create(ARCHITECT, model, ArchiType.ofSimpleName("ServingRelationship"),
                component.id().value(), actor.id().value(), Optional.of("обслуживает"),
                Optional.of(new RelationshipService.EdgePlacement(view.id(), c.id().value(), a.id().value())),
                Optional.empty());
        views.saveLayout(ARCHITECT, view.id(), Map.of(a.id(), new Bounds(320, 80, 150, 60)),
                Map.of(serving.edge().orElseThrow(), List.of(new Bendpoint(0, 40, -50, 0))));

        ArchitectureModel loaded = queries.open(VIEWER, model);
        View loadedView = queries.openView(VIEWER, view.id());

        Element reloaded = loaded.requireElement(component.id());
        assertEquals(Optional.of("Ядро банка"), reloaded.documentation());
        assertEquals(List.of("owner", "criticality"), reloaded.properties().stream().map(p -> p.key()).toList());
        assertEquals(1000, reloaded.sortOrder().value());
        assertEquals(1, loaded.relationships().size());
        assertEquals(9, loaded.roots().size());
        assertEquals(new Bounds(320, 80, 150, 60), loadedView.requireNode(a.id()).bounds());
        assertEquals(List.of(new Bendpoint(0, 40, -50, 0)),
                loadedView.requireEdge(serving.edge().orElseThrow()).bendpoints());
        assertTrue(loaded.view(view.id()).isPresent(), "дерево знает о представлении");
    }
}
