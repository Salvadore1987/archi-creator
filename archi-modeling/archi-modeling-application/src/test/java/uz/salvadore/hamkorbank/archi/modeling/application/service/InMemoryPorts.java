package uz.salvadore.hamkorbank.archi.modeling.application.service;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.AcquireLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateViewCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LockResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.AccessListGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.AccessListReplaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementTypeListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.FolderCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.LockAcquireCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.LockGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.LockReleaseCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelContents;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelPurgeCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelRestoreCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelValidateQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationTypeSuggestQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationshipCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationshipDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationshipEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.TreeItemRenameCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.TreeItemsDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.TreeItemsMoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionCommitter;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionContents;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionLabelCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionRestoreCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionSaveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewEdgeEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewEdgePlaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewEdgeRemoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewLayoutProposeQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewLayoutSaveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewLookup;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodeEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodeMoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodePlaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodeRemoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.WorkspaceResolveQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.DomainEventPublisher;
import uz.salvadore.hamkorbank.archi.modeling.application.port.IdempotencyRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelAccessListRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelLockRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelVersionRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotArchive;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotReader;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotWriter;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UseCaseMetrics;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewPlacements;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.AccessListApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ElementApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.LockApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.MetamodelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.RelationshipApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.TreeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.VersionApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.ModelAccessList;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotencyRecord;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Порты modeling в памяти — проверка сценариев без базы. Хранилище отдаёт копию
 * агрегата через {@code restore}, как настоящее: изменения, не записанные {@code save},
 * не видны следующему чтению. Транзакция — тоже как настоящая: отказ внутри записи
 * откатывает все хранилища к началу внешней транзакции, а в транзакции чтения
 * запись в хранилище — ошибка теста.
 */
final class InMemoryPorts {

    static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    final MutableClock clock = new MutableClock(NOW);
    final Map<ModelId, ArchitectureModel> models = new LinkedHashMap<>();
    final Map<ViewId, View> views = new LinkedHashMap<>();
    final Map<ModelId, ModelLock> locks = new HashMap<>();
    final List<ModelVersion> versions = new ArrayList<>();
    final Map<ModelId, ModelAccessList> acl = new HashMap<>();
    final Map<IdempotencyRecord.Key, IdempotencyRecord> keys = new HashMap<>();
    final List<DomainEvent> published = new ArrayList<>();
    final Workspace workspace = Workspace.restore(WorkspaceId.of(IDS.generate()), "Банк", NOW);

    final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T write(Supplier<T> work) {
            return transaction(false, work);
        }

        @Override
        public <T> T read(Supplier<T> work) {
            return transaction(true, work);
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
            return models.values().stream().map(ArchitectureModel::header)
                    .filter(h -> h.workspaceId().equals(workspaceId)).toList();
        }

        @Override
        public void save(ArchitectureModel model) {
            requireWritable();
            models.put(model.id(), copy(model));
        }

        @Override
        public void replaceContent(ModelContent content) {
            requireWritable();
            save(content.model());
            views.values().removeIf(v -> v.modelId().equals(content.model().id()));
            content.views().forEach(viewRepository::save);
        }

        @Override
        public void purge(ModelId id) {
            requireWritable();
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
                    .anyMatch(n -> elementId.equals(n.elementId()))).map(View::id).toList();
        }

        @Override
        public List<ViewId> viewsReferencing(RelationshipId relationshipId) {
            return views.values().stream().filter(v -> v.edges().values().stream()
                    .anyMatch(e -> relationshipId.equals(e.relationshipId()))).map(View::id).toList();
        }
    };

    final ViewRepository viewRepository = new ViewRepository() {
        @Override
        public Optional<View> load(ViewId id) {
            return Optional.ofNullable(views.get(id)).map(InMemoryPorts::copy);
        }

        @Override
        public List<View> loadAll(ModelId modelId) {
            return views.values().stream().filter(v -> v.modelId().equals(modelId)).map(InMemoryPorts::copy)
                    .toList();
        }

        @Override
        public Optional<ViewId> viewOfNode(ViewNodeId nodeId) {
            return views.values().stream().filter(v -> v.nodes().contains(nodeId)).map(View::id).findFirst();
        }

        @Override
        public Optional<ViewId> viewOfEdge(ViewEdgeId edgeId) {
            return views.values().stream().filter(v -> v.edges().contains(edgeId)).map(View::id).findFirst();
        }

        @Override
        public List<ViewPlacements> placements(ModelId modelId) {
            return views.values().stream().filter(v -> v.modelId().equals(modelId))
                    .map(v -> new ViewPlacements(v.id(),
                            v.nodes().values().stream().map(ViewNode::elementId).filter(Objects::nonNull).distinct()
                                    .toList(),
                            v.edges().values().stream().map(ViewEdge::relationshipId).filter(Objects::nonNull)
                                    .distinct().toList()))
                    .filter(p -> !p.elementIds().isEmpty() || !p.relationshipIds().isEmpty())
                    .toList();
        }

        @Override
        public void save(View view) {
            requireWritable();
            views.put(view.id(), copy(view));
        }

        @Override
        public void delete(ViewId id) {
            requireWritable();
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
            requireWritable();
            locks.put(lock.modelId(), lock);
        }

        @Override
        public void delete(ModelId modelId) {
            requireWritable();
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
        public List<ModelVersion> page(ModelId modelId, long before, int size) {
            return list(modelId).stream().filter(v -> v.versionNo() < before).limit(size).toList();
        }

        @Override
        public void save(ModelVersion version) {
            requireWritable();
            versions.removeIf(v -> v.id().equals(version.id()));
            versions.add(version);
        }

        @Override
        public List<ModelId> modelsWithSnapshots() {
            return versions.stream().filter(ModelVersion::hasSnapshot).map(ModelVersion::modelId)
                    .distinct().toList();
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
            requireWritable();
            acl.put(list.modelId(), list);
        }
    };

    final IdempotencyRepository idempotencyRepository = new IdempotencyRepository() {
        @Override
        public Optional<IdempotencyRecord> find(String scope, String actor, String key) {
            return Optional.ofNullable(keys.get(new IdempotencyRecord.Key(scope, actor, key)));
        }

        @Override
        public void save(IdempotencyRecord record) {
            requireWritable();
            keys.put(new IdempotencyRecord.Key(record.scope(), record.actor(), record.key()), record);
        }
    };

    final WorkspaceRepository workspaceRepository = new WorkspaceRepository() {
        @Override
        public List<Workspace> findAll() {
            return List.of(workspace);
        }

        @Override
        public Optional<Workspace> first() {
            return Optional.of(workspace);
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
    final SnapshotWriter snapshotWriter = rows -> (rows.header().name() + "|"
            + rows.elements().stream().map(e -> e.name()).collect(Collectors.joining(","))
            + "|" + rows.relationships().size() + "|" + rows.views().size())
            .getBytes(StandardCharsets.UTF_8);

    final DomainEventPublisher events = published::addAll;

    final ModelingKernel kernel = new ModelingKernel(modelRepository, viewRepository, lockRepository,
            versionRepository, aclRepository, idempotencyRepository, unitOfWork, events, UseCaseMetrics.NONE,
            Message::toString, clock, Duration.ofMinutes(30));
    final VersionCommitter committer = new VersionCommitter(kernel, snapshotWriter);
    final WorkspaceResolveQueryHandler resolveWorkspace = new WorkspaceResolveQueryHandler(workspaceRepository,
            unitOfWork);
    final ModelApplicationService lifecycle = new ModelApplicationServiceImpl(
            new ModelCreateCommandHandler(kernel, workspaceRepository, resolveWorkspace,
                    committer),
            new ModelEditCommandHandler(kernel), new ModelDeleteCommandHandler(kernel),
            new ModelRestoreCommandHandler(kernel), new ModelPurgeCommandHandler(kernel));
    final ModelQueryApplicationService queries = new ModelQueryApplicationServiceImpl(
            new ModelListQueryHandler(kernel, resolveWorkspace), new ModelGetQueryHandler(kernel),
            new ModelValidateQueryHandler(kernel));
    final LockApplicationService lockService = new LockApplicationServiceImpl(new LockAcquireCommandHandler(kernel),
            new LockReleaseCommandHandler(kernel), new LockGetQueryHandler(kernel));
    final MetamodelApplicationService metamodel = new MetamodelApplicationServiceImpl(
            new ElementTypeListQueryHandler(), new RelationTypeSuggestQueryHandler());
    final AccessListApplicationService accessLists = new AccessListApplicationServiceImpl(
            new AccessListGetQueryHandler(kernel), new AccessListReplaceCommandHandler(kernel));
    private final VersionContents withoutArchive = new VersionContents(kernel, committer, snapshotWriter,
            xml -> {
                throw new UnsupportedOperationException();
            }, SnapshotArchive.NONE);
    private final ModelContents contents = new ModelContents(kernel);
    private final ViewLookup viewLookup = new ViewLookup(kernel);
    final VersionApplicationService versionService = new VersionApplicationServiceImpl(
            new VersionSaveCommandHandler(kernel, committer), new VersionListQueryHandler(kernel),
            new VersionLabelCommandHandler(kernel, withoutArchive),
            new VersionRestoreCommandHandler(kernel, withoutArchive));
    final ElementApplicationService elements = new ElementApplicationServiceImpl(
            new ElementCreateCommandHandler(kernel), new ElementEditCommandHandler(kernel, contents),
            new ElementDeleteCommandHandler(kernel, contents));
    final RelationshipApplicationService relationships = new RelationshipApplicationServiceImpl(
            new RelationshipCreateCommandHandler(kernel), new RelationshipEditCommandHandler(kernel, contents),
            new RelationshipDeleteCommandHandler(kernel, contents));
    final ViewApplicationService viewService = new ViewApplicationServiceImpl(new ViewCreateCommandHandler(kernel),
            new ViewNodePlaceCommandHandler(kernel, viewLookup), new ViewEdgePlaceCommandHandler(kernel, viewLookup),
            new ViewNodeMoveCommandHandler(kernel, viewLookup), new ViewNodeEditCommandHandler(kernel, viewLookup),
            new ViewEdgeEditCommandHandler(kernel, viewLookup), new ViewNodeRemoveCommandHandler(kernel, viewLookup),
            new ViewEdgeRemoveCommandHandler(kernel, viewLookup), new ViewLayoutSaveCommandHandler(kernel, viewLookup));
    final ViewQueryApplicationService viewQueries = new ViewQueryApplicationServiceImpl(
            new ViewGetQueryHandler(kernel, viewLookup), new ViewLayoutProposeQueryHandler(kernel, viewLookup));
    final TreeApplicationService tree = new TreeApplicationServiceImpl(new FolderCreateCommandHandler(kernel),
            new TreeItemRenameCommandHandler(kernel), new TreeItemsMoveCommandHandler(kernel),
            new TreeItemsDeleteCommandHandler(kernel, contents));

    /** Глубина вложенных транзакций: больше нуля — работа идёт внутри транзакции. */
    private int transactionDepth;
    /** Внешняя транзакция открыта на чтение: вложенные к ней присоединяются и тоже только читают. */
    private boolean readOnly;
    /** Вложенная транзакция завершилась отказом: внешняя уже не зафиксируется. */
    private boolean rollbackOnly;

    /**
     * Транзакция с распространением «присоединиться к текущей»: вложенный вызов работает
     * в транзакции внешнего. Отказ откатывает всё к началу внешней транзакции; если внешняя
     * перехватила отказ вложенной и завершилась успешно, фиксация всё равно не происходит.
     */
    private <T> T transaction(boolean read, Supplier<T> work) {
        if (transactionDepth > 0) {
            return joined(work);
        }
        Snapshot before = new Snapshot();
        readOnly = read;
        rollbackOnly = false;
        transactionDepth++;
        boolean committed = false;
        try {
            T result = work.get();
            if (rollbackOnly) {
                throw new IllegalStateException("транзакция помечена на откат вложенным отказом");
            }
            committed = true;
            return result;
        } finally {
            if (!committed) {
                before.restore();
            }
            transactionDepth--;
            readOnly = false;
            rollbackOnly = false;
        }
    }

    private <T> T joined(Supplier<T> work) {
        transactionDepth++;
        boolean completed = false;
        try {
            T result = work.get();
            completed = true;
            return result;
        } finally {
            if (!completed) {
                rollbackOnly = true;
            }
            transactionDepth--;
        }
    }

    private void requireWritable() {
        if (readOnly) {
            throw new IllegalStateException("запись в транзакции чтения");
        }
    }

    /** Идёт ли сейчас транзакция — чтобы проверить, что внешний вызов сделан вне её. */
    boolean inTransaction() {
        return transactionDepth > 0;
    }

    /** Содержимое версий с заданными чтением снимка и архивом — для сценариев отката и выгрузки. */
    VersionContents versionContents(SnapshotReader reader, SnapshotArchive archive) {
        return new VersionContents(kernel, committer, snapshotWriter, reader, archive);
    }

    /** Новая модель в пространстве теста — короткий путь для сценариев, которым она нужна. */
    ModelId createModel(EditorIdentity actor, String name) {
        return ModelId.of(lifecycle.createModel(actor, new CreateModelCommand(workspace.id().value(), name, null))
                .id());
    }

    /** Новый элемент в корне слоя с идентификаторами от сервера — короткий путь. */
    ElementResponse element(EditorIdentity actor, ModelId model, ArchiType type, String name) {
        return elements.createElement(actor, new CreateElementCommand(model.value(), type.value(), name, null, null,
                null, null));
    }

    /** Связь без имени и ребра с идентификаторами от сервера — короткий путь. */
    RelationshipResponse relate(EditorIdentity actor, ModelId model, ArchiType type, UUID source, UUID target) {
        return relationships.createRelationship(actor, new CreateRelationshipCommand(model.value(), type.value(),
                source, target, null, null, null, null, null, null, null, null)).relationship();
    }

    /** Новое представление в корне представлений с идентификаторами от сервера — короткий путь. */
    ViewSummaryResponse view(EditorIdentity actor, ModelId model, String name) {
        return viewService.createView(actor, new CreateViewCommand(model.value(), name, null, null, null, null));
    }

    /** Узел элемента с заданными границами и идентификаторами от сервера — короткий путь. */
    ViewNodeResponse place(EditorIdentity actor, UUID view, UUID element, Bounds bounds) {
        return viewService.placeNode(actor, new PlaceViewNodeCommand(view, null, element, null, null, bounds.x(),
                bounds.y(), bounds.width(), bounds.height(), null, null, null)).node();
    }

    /** Блокировка модели автором — короткий путь для сценариев, которым нужна запись. */
    LockResponse lock(EditorIdentity actor, ModelId model) {
        return lockService.acquireLock(actor, new AcquireLockCommand(model.value()));
    }

    private static ArchitectureModel copy(ArchitectureModel model) {
        return ArchitectureModel.restore(model.header(), model.folders().values(), model.elements().values(),
                model.relationships().values(), model.views());
    }

    private static View copy(View view) {
        return View.restore(view.header(), view.nodes().values(), view.edges().values());
    }

    /**
     * Состояние всех хранилищ на начало транзакции. Значения в хранилищах не меняются
     * на месте — запись кладёт новую копию, — поэтому копии самих коллекций достаточно.
     */
    private final class Snapshot {
        private final Map<ModelId, ArchitectureModel> models = new LinkedHashMap<>(InMemoryPorts.this.models);
        private final Map<ViewId, View> views = new LinkedHashMap<>(InMemoryPorts.this.views);
        private final Map<ModelId, ModelLock> locks = new HashMap<>(InMemoryPorts.this.locks);
        private final List<ModelVersion> versions = new ArrayList<>(InMemoryPorts.this.versions);
        private final Map<ModelId, ModelAccessList> acl = new HashMap<>(InMemoryPorts.this.acl);
        private final Map<IdempotencyRecord.Key, IdempotencyRecord> keys = new HashMap<>(InMemoryPorts.this.keys);
        private final List<DomainEvent> published = new ArrayList<>(InMemoryPorts.this.published);

        void restore() {
            replace(InMemoryPorts.this.models, models);
            replace(InMemoryPorts.this.views, views);
            replace(InMemoryPorts.this.locks, locks);
            replace(InMemoryPorts.this.acl, acl);
            replace(InMemoryPorts.this.keys, keys);
            InMemoryPorts.this.versions.clear();
            InMemoryPorts.this.versions.addAll(versions);
            InMemoryPorts.this.published.clear();
            InMemoryPorts.this.published.addAll(published);
        }

        private static <K, V> void replace(Map<K, V> target, Map<K, V> source) {
            target.clear();
            target.putAll(source);
        }
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
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
