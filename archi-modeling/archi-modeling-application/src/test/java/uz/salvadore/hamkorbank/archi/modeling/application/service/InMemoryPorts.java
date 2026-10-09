package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import uz.salvadore.hamkorbank.archi.modeling.application.port.DomainEventPublisher;
import uz.salvadore.hamkorbank.archi.modeling.application.port.GitBinding;
import uz.salvadore.hamkorbank.archi.modeling.application.port.IdempotencyRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelAccessListRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelLockRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelVersionRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotWriter;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UseCaseMetrics;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.ModelAccessList;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotencyRecord;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Порты modeling в памяти — проверка сценариев без базы. Хранилище отдаёт копию
 * агрегата через {@code restore}, как настоящее: изменения, не записанные {@code save},
 * не видны следующему чтению.
 */
final class InMemoryPorts {

    static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    final MutableClock clock = new MutableClock(NOW);
    final Map<ModelId, ArchitectureModel> models = new LinkedHashMap<>();
    final Map<ViewId, View> views = new LinkedHashMap<>();
    final Map<ModelId, ModelLock> locks = new HashMap<>();
    final List<ModelVersion> versions = new ArrayList<>();
    final Map<ModelId, ModelAccessList> acl = new HashMap<>();
    final Map<String, IdempotencyRecord> keys = new HashMap<>();
    final List<DomainEvent> published = new ArrayList<>();
    final Workspace workspace = new Workspace(WorkspaceId.of(UUID.randomUUID()), "Банк", NOW);

    final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T write(Supplier<T> work) {
            return work.get();
        }

        @Override
        public <T> T read(Supplier<T> work) {
            return work.get();
        }
    };

    final ModelRepository modelRepository = new ModelRepository() {
        @Override
        public Optional<ArchitectureModel> load(ModelId id) {
            return Optional.ofNullable(models.get(id)).map(InMemoryPorts::copy);
        }

        @Override
        public Optional<ModelHeader> findHeader(ModelId id) {
            return Optional.ofNullable(models.get(id)).map(ArchitectureModel::header);
        }

        @Override
        public List<ModelHeader> list(WorkspaceId workspaceId) {
            return models.values().stream().map(ArchitectureModel::header).toList();
        }

        @Override
        public void save(ArchitectureModel model) {
            models.put(model.id(), copy(model));
        }

        @Override
        public void replaceContent(ModelContent content) {
            save(content.model());
            views.values().removeIf(v -> v.modelId().equals(content.model().id()));
            content.views().forEach(viewRepository::save);
        }

        @Override
        public void purge(ModelId id) {
            models.remove(id);
        }

        @Override
        public Optional<ModelId> ownerOf(UUID objectId) {
            return models.values().stream().filter(m -> m.elements().contains(ElementId.of(objectId))
                    || m.relationships().contains(RelationshipId.of(objectId))).map(ArchitectureModel::id).findFirst();
        }

        @Override
        public boolean idTaken(UUID id) {
            return models.values().stream().anyMatch(m -> m.folders().contains(FolderId.of(id))
                    || m.elements().contains(ElementId.of(id)) || m.relationships().contains(RelationshipId.of(id)))
                    || views.containsKey(ViewId.of(id))
                    || views.values().stream().anyMatch(v -> v.nodes().contains(ViewNodeId.of(id))
                    || v.edges().contains(ViewEdgeId.of(id)));
        }

        @Override
        public boolean diagramArchiIdTaken(ModelId modelId, ArchiId archiId) {
            return views.values().stream().filter(v -> v.modelId().equals(modelId))
                    .anyMatch(v -> v.nodes().values().stream().anyMatch(n -> n.archiId().equals(archiId))
                            || v.edges().values().stream().anyMatch(e -> e.archiId().equals(archiId)));
        }

        @Override
        public List<ViewId> viewsReferencing(ElementId elementId) {
            return views.values().stream().filter(v -> v.nodes().values().stream()
                    .anyMatch(n -> n.elementId().equals(Optional.of(elementId)))).map(View::id).toList();
        }

        @Override
        public List<ViewId> viewsReferencing(RelationshipId relationshipId) {
            return views.values().stream().filter(v -> v.edges().values().stream()
                    .anyMatch(e -> e.relationshipId().equals(Optional.of(relationshipId)))).map(View::id).toList();
        }
    };

    final ViewRepository viewRepository = new ViewRepository() {
        @Override
        public Optional<View> load(ViewId id) {
            return Optional.ofNullable(views.get(id)).map(v -> View.restore(v.header(), v.nodes().values(),
                    v.edges().values()));
        }

        @Override
        public List<View> loadAll(ModelId modelId) {
            return views.values().stream().filter(v -> v.modelId().equals(modelId)).toList();
        }

        @Override
        public Optional<ViewId> viewOfNode(ViewNodeId nodeId) {
            return views.values().stream().filter(v -> v.nodes().contains(nodeId)).map(View::id).findFirst();
        }

        @Override
        public void save(View view) {
            views.put(view.id(), View.restore(view.header(), view.nodes().values(), view.edges().values()));
        }

        @Override
        public void delete(ViewId id) {
            views.remove(id);
        }
    };

    final ModelLockRepository lockRepository = new ModelLockRepository() {
        @Override
        public Optional<ModelLock> find(ModelId modelId) {
            return Optional.ofNullable(locks.get(modelId));
        }

        @Override
        public void save(ModelLock lock) {
            locks.put(lock.modelId(), lock);
        }

        @Override
        public void delete(ModelId modelId) {
            locks.remove(modelId);
        }
    };

    final ModelVersionRepository versionRepository = new ModelVersionRepository() {
        @Override
        public Optional<ModelVersion> find(ModelId modelId, long versionNo) {
            return versions.stream().filter(v -> v.modelId().equals(modelId) && v.versionNo() == versionNo).findFirst();
        }

        @Override
        public Optional<ModelVersion> last(ModelId modelId) {
            return versions.stream().filter(v -> v.modelId().equals(modelId))
                    .max(Comparator.comparingLong(ModelVersion::versionNo));
        }

        @Override
        public List<ModelVersion> list(ModelId modelId) {
            return versions.stream().filter(v -> v.modelId().equals(modelId))
                    .sorted(Comparator.comparingLong(ModelVersion::versionNo).reversed()).toList();
        }

        @Override
        public void save(ModelVersion version) {
            versions.removeIf(v -> v.id().equals(version.id()));
            versions.add(version);
        }

        @Override
        public List<ModelId> modelsWithSnapshots() {
            return versions.stream().filter(v -> v.snapshot().isPresent()).map(ModelVersion::modelId).distinct().toList();
        }
    };

    final ModelAccessListRepository aclRepository = new ModelAccessListRepository() {
        @Override
        public ModelAccessList find(ModelId modelId) {
            return acl.getOrDefault(modelId, ModelAccessList.open(modelId));
        }

        @Override
        public Map<ModelId, ModelAccessList> findAll(Collection<ModelId> modelIds) {
            return modelIds.stream().collect(Collectors.toMap(id -> id, this::find));
        }

        @Override
        public void save(ModelAccessList list) {
            acl.put(list.modelId(), list);
        }
    };

    final IdempotencyRepository idempotencyRepository = new IdempotencyRepository() {
        @Override
        public Optional<IdempotencyRecord> find(String scope, String actor, String key) {
            return Optional.ofNullable(keys.get(scope + actor + key));
        }

        @Override
        public void save(IdempotencyRecord record) {
            keys.put(record.scope() + record.actor() + record.key(), record);
        }
    };

    final WorkspaceRepository workspaceRepository = new WorkspaceRepository() {
        @Override
        public List<Workspace> findAll() {
            return List.of(workspace);
        }

        @Override
        public Optional<Workspace> find(WorkspaceId id) {
            return Optional.of(workspace).filter(w -> w.id().equals(id));
        }

        @Override
        public void insert(Workspace w) {
            throw new UnsupportedOperationException();
        }
    };

    /** Снимок-заглушка: имена всего содержимого по порядку — меняется, когда меняется модель. */
    final SnapshotWriter snapshotWriter = content -> (content.model().name() + "|"
            + content.model().elements().values().stream().map(e -> e.name()).collect(Collectors.joining(","))
            + "|" + content.model().relationships().size() + "|" + content.views().size())
            .getBytes(StandardCharsets.UTF_8);

    final DomainEventPublisher events = published::addAll;
    final GitBinding noGit = workspaceId -> false;

    final ModelingKernel kernel = new ModelingKernel(modelRepository, viewRepository, lockRepository,
            versionRepository, aclRepository, idempotencyRepository, unitOfWork, events, UseCaseMetrics.NONE, clock,
            Duration.ofMinutes(30));
    final VersionService versionService = new VersionService(kernel, snapshotWriter, (xml, current, now) -> {
        throw new UnsupportedOperationException();
    }, noGit, ZoneOffset.UTC, false);
    final ModelLifecycleService lifecycle = new ModelLifecycleService(kernel, workspaceRepository, versionService);
    final ModelQueryService queries = new ModelQueryService(kernel);
    final LockService lockService = new LockService(kernel);
    final ElementService elements = new ElementService(kernel);
    final RelationshipService relationships = new RelationshipService(kernel, elements);
    final ViewService viewService = new ViewService(kernel);
    final TreeService tree = new TreeService(kernel, elements, relationships);
    final AccessListService accessLists = new AccessListService(kernel);

    private static ArchitectureModel copy(ArchitectureModel model) {
        return ArchitectureModel.restore(model.header(), model.folders().values(), model.elements().values(),
                model.relationships().values(), model.views());
    }

    /** Часы, которые тест переводит вперёд — истечение блокировки. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
