package uz.salvadore.hamkorbank.archi.bootstrap.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.PropertyValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.AcquireLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CreateRelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ElementApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.LockApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.RelationshipApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ViewService;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;

class ModelPersistenceIT extends IntegrationTest {

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Autowired
    ModelApplicationService lifecycle;
    @Autowired
    LockApplicationService locks;
    @Autowired
    ElementApplicationService elements;
    @Autowired
    RelationshipApplicationService relationships;
    @Autowired
    ViewService views;
    @Autowired
    ModelQueryApplicationService queries;
    @Autowired
    ModelQueryService viewQueries;

    @Test
    @DisplayName("INV-MDL-001: дубль archi_id в модели отклоняет база — UNIQUE (model_id, archi_id)")
    void duplicateArchiIdViolatesUniqueConstraint() {
        ModelId model = ModelId.of(lifecycle.createModel(ARCHITECT,
                new CreateModelCommand(workspace().value(), "Уникальность", null)).id());
        locks.acquireLock(ARCHITECT, new AcquireLockCommand(model.value()));
        ElementResponse element = elements.createElement(ARCHITECT, new CreateElementCommand(model.value(),
                ArchiType.ofSimpleName("ApplicationComponent").value(), "АБС", null, null, null, null));
        UUID folder = jdbc.queryForObject("select folder_id from element where id = ?", UUID.class, element.id());

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into element (id, model_id, folder_id, archi_id, archi_type, layer, name, sort_order, supported)
                values (?, ?, ?, ?, 'archimate:ApplicationComponent', 'APPLICATION', 'Двойник', 9000, true)
                """, IDS.generate(), model.value(), folder, element.archiId()));
    }

    @Test
    @DisplayName("§4.3: дерево и представление переживают запись и чтение — поля, порядок, геометрия")
    void modelAndViewSurviveStorage() {
        ModelId model = ModelId.of(lifecycle.createModel(ARCHITECT,
                new CreateModelCommand(workspace().value(), "Хранение", "persist-1")).id());
        locks.acquireLock(ARCHITECT, new AcquireLockCommand(model.value()));
        ElementResponse component = elements.createElement(ARCHITECT, new CreateElementCommand(model.value(),
                ArchiType.ofSimpleName("ApplicationComponent").value(), "АБС", null, null, null, null));
        ElementResponse actor = elements.createElement(ARCHITECT, new CreateElementCommand(model.value(),
                ArchiType.ofSimpleName("BusinessActor").value(), "Клиент", null, null, null, null));
        elements.editElement(ARCHITECT, new EditElementCommand(component.id(), null, "Ядро банка",
                List.of(new PropertyValue("owner", "ИТ"), new PropertyValue("criticality", "high"))));
        View view = views.create(ARCHITECT, model, "Контекст", null, null);
        ViewNode c = views.place(ARCHITECT, view.id(), ElementId.of(component.id()), new Bounds(20, 60, 150, 60),
                null);
        ViewNode a = views.place(ARCHITECT, view.id(), ElementId.of(actor.id()), new Bounds(300, 60, 150, 60),
                null);
        CreateRelationshipResponse serving = relationships.createRelationship(ARCHITECT,
                new CreateRelationshipCommand(model.value(), ArchiType.ofSimpleName("ServingRelationship").value(),
                        component.id(), actor.id(), "обслуживает", null, null, null, null, null,
                        new CreateRelationshipCommand.Edge(view.id().value(), c.id().value(), a.id().value(), null,
                                null),
                        null));
        views.saveLayout(ARCHITECT, view.id(), Map.of(a.id(), new Bounds(320, 80, 150, 60)),
                Map.of(ViewEdgeId.of(Optional.ofNullable(serving.edgeId()).orElseThrow()),
                        List.of(new Bendpoint(0, 40, -50, 0))));

        ModelTreeResponse loaded = queries.getModel(VIEWER, new GetModelQuery(model.value()));
        View loadedView = viewQueries.openView(VIEWER, view.id());

        ElementResponse reloaded = loaded.elements().stream()
                .filter(e -> e.id().equals(component.id())).findFirst().orElseThrow();
        assertEquals("Ядро банка", reloaded.documentation());
        assertEquals(List.of("owner", "criticality"), reloaded.properties().stream().map(p -> p.key()).toList());
        assertEquals(1000, reloaded.sortOrder());
        assertEquals(1, loaded.relationships().size());
        assertEquals(9, loaded.folders().stream().filter(folder -> folder.parentId() == null).count());
        assertEquals(new Bounds(320, 80, 150, 60), loadedView.requireNode(a.id()).bounds());
        assertEquals(List.of(new Bendpoint(0, 40, -50, 0)),
                loadedView.requireEdge(ViewEdgeId.of(serving.edgeId())).bendpoints());
        assertTrue(loaded.views().stream().anyMatch(v -> v.id().equals(view.id().value())),
                "дерево знает о представлении");
    }
}
