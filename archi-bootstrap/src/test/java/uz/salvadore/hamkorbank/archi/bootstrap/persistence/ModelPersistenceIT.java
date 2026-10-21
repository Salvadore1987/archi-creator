package uz.salvadore.hamkorbank.archi.bootstrap.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.PropertyValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.AcquireLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateViewCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveViewLayoutCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetViewQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListModelsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CreateRelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ConcurrentUpdateException;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelLockRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelVersionRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ElementApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.LockApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.RelationshipApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.VersionApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

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
    ViewApplicationService views;
    @Autowired
    ModelQueryApplicationService queries;
    @Autowired
    ViewQueryApplicationService viewQueries;
    @Autowired
    ModelLockRepository lockRows;
    @Autowired
    VersionApplicationService versions;
    @Autowired
    ModelVersionRepository versionRows;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    EntityManagerFactory entityManagers;

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
    @DisplayName("отказ записи блокировки, не связанный с гонкой, не выдаётся за занятую блокировку")
    void nonConflictWriteFailureIsNotReportedAsLockConflict() {
        Instant now = Instant.now();
        ModelLock ofMissingModel = ModelLock.restore(ModelId.of(IDS.generate()), ARCHITECT.subject(), now,
                now.plusSeconds(60));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> lockRows.save(ofMissingModel)));

        assertFalse(failure instanceof ConcurrentUpdateException, failure.toString());
    }

    @Test
    @DisplayName("INV-MDL-006: два захвата просроченной блокировки — второй получает конфликт, а не перезапись")
    void concurrentTakeoverOfExpiredLockConflicts() {
        ModelId model = ModelId.of(lifecycle.createModel(ARCHITECT,
                new CreateModelCommand(workspace().value(), "Две руки", null)).id());
        Instant now = Instant.now();
        jdbc.update("insert into model_lock (model_id, owner, acquired_at, expires_at) values (?, 'gone', ?, ?)",
                model.value(), Timestamp.from(now.minusSeconds(7200)), Timestamp.from(now.minusSeconds(3600)));
        TransactionTemplate tx = new TransactionTemplate(transactions);

        assertThrows(ConcurrentUpdateException.class, () -> tx.executeWithoutResult(status -> {
            lockRows.find(model).orElseThrow();
            CompletableFuture.runAsync(() -> tx.executeWithoutResult(other -> {
                lockRows.find(model).orElseThrow();
                lockRows.save(ModelLock.restore(model, OTHER_ARCHITECT.subject(), now, now.plusSeconds(600)));
            })).join();
            lockRows.save(ModelLock.restore(model, ARCHITECT.subject(), now, now.plusSeconds(600)));
        }));
        assertEquals(OTHER_ARCHITECT.subject(),
                jdbc.queryForObject("select owner from model_lock where model_id = ?", String.class, model.value()));
    }

    @Test
    @DisplayName("метка версии, поставленная во время публикации, не стирает записанный публикацией git_sha")
    void labelDoesNotOverwriteConcurrentGitSha() {
        ModelId model = ModelId.of(lifecycle.createModel(ARCHITECT,
                new CreateModelCommand(workspace().value(), "Метка и коммит", null)).id());
        locks.acquireLock(ARCHITECT, new AcquireLockCommand(model.value()));
        versions.saveVersion(ARCHITECT, new SaveVersionCommand(model.value(), "первая", null));
        String sha = "b".repeat(40);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            ModelVersion read = versionRows.last(model).orElseThrow();
            CompletableFuture.runAsync(() -> jdbc.update("update model_version set git_sha = ? where id = ?", sha,
                    read.id().value())).join();
            versionRows.save(read.labelled("релиз"));
        });

        assertEquals(List.of(sha + " релиз"), column(
                "select git_sha || ' ' || label from model_version where model_id = ?", String.class, model.value()));
    }

    @Test
    @DisplayName("FR-04: список моделей читается числом запросов, не зависящим от числа моделей")
    void modelListQueryCountDoesNotGrowWithModels() {
        createModels(2, "Список-а");
        long few = statementsOf(() -> queries.listModels(ARCHITECT, new ListModelsQuery(workspace().value())));
        createModels(4, "Список-б");
        long many = statementsOf(() -> queries.listModels(ARCHITECT, new ListModelsQuery(workspace().value())));

        assertEquals(few, many);
    }

    private void createModels(int count, String prefix) {
        for (int i = 0; i < count; i++) {
            lifecycle.createModel(ARCHITECT, new CreateModelCommand(workspace().value(), prefix + " " + i, null));
        }
    }

    private long statementsOf(Runnable work) {
        Statistics statistics = entityManagers.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            work.run();
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
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
        ViewSummaryResponse view = views.createView(ARCHITECT,
                new CreateViewCommand(model.value(), "Контекст", null, null, null, null));
        ViewNodeResponse c = views.placeNode(ARCHITECT,
                new PlaceViewNodeCommand(view.id(), null, component.id(), null, null, 20, 60, 150, 60, null, null,
                        null)).node();
        ViewNodeResponse a = views.placeNode(ARCHITECT,
                new PlaceViewNodeCommand(view.id(), null, actor.id(), null, null, 300, 60, 150, 60, null, null,
                        null)).node();
        CreateRelationshipResponse serving = relationships.createRelationship(ARCHITECT,
                new CreateRelationshipCommand(model.value(), ArchiType.ofSimpleName("ServingRelationship").value(),
                        component.id(), actor.id(), "обслуживает", null, null, null, null, null,
                        new CreateRelationshipCommand.Edge(view.id(), c.id(), a.id(), null, null),
                        null));
        views.saveLayout(ARCHITECT, new SaveViewLayoutCommand(view.id(),
                List.of(new SaveViewLayoutCommand.NodeBounds(a.id(), 320, 80, 150, 60)),
                List.of(new SaveViewLayoutCommand.EdgeBendpoints(Optional.ofNullable(serving.edgeId()).orElseThrow(),
                        List.of(new BendpointValue(0, 40, -50, 0))))));

        ModelTreeResponse loaded = queries.getModel(VIEWER, new GetModelQuery(model.value()));
        ViewResponse loadedView = viewQueries.getView(VIEWER, new GetViewQuery(view.id()));

        ElementResponse reloaded = loaded.elements().stream()
                .filter(e -> e.id().equals(component.id())).findFirst().orElseThrow();
        assertEquals("Ядро банка", reloaded.documentation());
        assertEquals(List.of("owner", "criticality"), reloaded.properties().stream().map(p -> p.key()).toList());
        assertEquals(1000, reloaded.sortOrder());
        assertEquals(1, loaded.relationships().size());
        assertEquals(9, loaded.folders().stream().filter(folder -> folder.parentId() == null).count());
        ViewNodeResponse moved = loadedView.nodes().stream().filter(n -> n.id().equals(a.id())).findFirst()
                .orElseThrow();
        assertEquals(List.of(320, 80, 150, 60), List.of(moved.x(), moved.y(), moved.width(), moved.height()));
        assertEquals(List.of(new BendpointValue(0, 40, -50, 0)), loadedView.edges().stream()
                .filter(e -> e.id().equals(serving.edgeId())).findFirst().orElseThrow().bendpoints());
        assertTrue(loaded.views().stream().anyMatch(v -> v.id().equals(view.id())),
                "дерево знает о представлении");
    }
}
