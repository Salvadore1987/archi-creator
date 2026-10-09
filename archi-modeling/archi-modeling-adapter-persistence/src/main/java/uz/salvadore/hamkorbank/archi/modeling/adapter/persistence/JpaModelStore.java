package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ElementEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.FolderEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ModelEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.RelationshipEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ViewEdgeEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ViewEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ViewNodeEntity;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ConcurrentModificationException;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewPlacements;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.TrackedMap;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Хранение модели и представлений. Дерево читается по запросу
 * на таблицу — папки, элементы со свойствами, связи со свойствами, места представлений, —
 * а не обходом ассоциаций: число запросов не зависит от размера модели.
 *
 * <p>Пишется только то, что агрегат отметил изменённым. Любое изменение поднимает
 * версию строки модели или представления ({@code OPTIMISTIC_FORCE_INCREMENT}): два
 * параллельных писателя одной модели не перезаписывают друг друга, даже
 * если тронули разные элементы.
 */
@Repository
public class JpaModelStore implements ModelRepository, ViewRepository {

    @PersistenceContext
    private EntityManager em;

    // ── Модель ──────────────────────────────────────────────────────

    @Override
    public Optional<ArchitectureModel> load(ModelId id) {
        ModelEntity model = em.find(ModelEntity.class, id.value());
        if (model == null) {
            return Optional.empty();
        }
        UUID modelId = id.value();
        var folders = em.createQuery("select f from FolderEntity f where f.modelId = :m", FolderEntity.class)
                .setParameter("m", modelId).getResultList();
        var elements = em.createQuery(
                        "select distinct e from ElementEntity e left join fetch e.properties where e.modelId = :m",
                        ElementEntity.class)
                .setParameter("m", modelId).getResultList();
        var relationships = em.createQuery(
                        "select distinct r from RelationshipEntity r left join fetch r.properties where r.modelId = :m",
                        RelationshipEntity.class)
                .setParameter("m", modelId).getResultList();
        var views = em.createQuery("select v from ViewEntity v where v.modelId = :m", ViewEntity.class)
                .setParameter("m", modelId).getResultList();
        return Optional.of(ArchitectureModel.restore(EntityMapper.toHeader(model),
                folders.stream().map(EntityMapper::toFolder).toList(),
                elements.stream().map(EntityMapper::toElement).toList(),
                relationships.stream().map(EntityMapper::toRelationship).toList(),
                views.stream().map(EntityMapper::toViewRef).toList()));
    }

    @Override
    public Optional<ModelHeader> findHeader(ModelId id) {
        return Optional.ofNullable(em.find(ModelEntity.class, id.value())).map(EntityMapper::toHeader);
    }

    @Override
    public List<ModelHeader> list(WorkspaceId workspaceId) {
        return em.createQuery("select m from ModelEntity m where m.workspaceId = :w order by m.name, m.id",
                        ModelEntity.class)
                .setParameter("w", workspaceId.value()).getResultList().stream().map(EntityMapper::toHeader).toList();
    }

    @Override
    public void save(ArchitectureModel model) {
        UUID modelId = model.id().value();
        if (model.fresh()) {
            ModelEntity entity = new ModelEntity();
            EntityMapper.apply(model.header(), entity);
            em.persist(entity);
        } else {
            ModelEntity entity = requireModel(model.id());
            if (model.headerChanged()) {
                EntityMapper.apply(model.header(), entity);
            }
            if (model.headerChanged() || model.folders().changed() || model.elements().changed()
                    || model.relationships().changed()) {
                em.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            }
        }
        sync(model.folders(), FolderEntity.class, FolderEntity::new, (d, e) -> EntityMapper.apply(d, modelId, e));
        sync(model.elements(), ElementEntity.class, ElementEntity::new, (d, e) -> EntityMapper.apply(d, modelId, e));
        sync(model.relationships(), RelationshipEntity.class, RelationshipEntity::new,
                (d, e) -> EntityMapper.apply(d, modelId, e));
        flush("модель " + model.id());
    }

    @Override
    public void replaceContent(ModelContent content) {
        ModelId id = content.model().id();
        ModelEntity entity = requireModel(id);
        if (entity.version != content.model().version()) {
            throw new ConcurrentModificationException("модель " + id);
        }
        em.flush();
        for (String table : List.of("ViewEdgeEntity", "ViewNodeEntity", "ViewEntity", "RelationshipEntity",
                "ElementEntity", "FolderEntity")) {
            em.createQuery("delete from " + table + " x where x.modelId = :m").setParameter("m", id.value())
                    .executeUpdate();
        }
        em.clear();
        ModelEntity fresh = requireModel(id);
        EntityMapper.apply(content.model().header(), fresh);
        em.lock(fresh, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        UUID modelId = id.value();
        content.model().folders().values().forEach(f -> persist(new FolderEntity(), e -> EntityMapper.apply(f, modelId, e)));
        content.model().elements().values().forEach(x -> persist(new ElementEntity(), e -> EntityMapper.apply(x, modelId, e)));
        content.model().relationships().values()
                .forEach(x -> persist(new RelationshipEntity(), e -> EntityMapper.apply(x, modelId, e)));
        content.model().folders().markPersisted();
        content.model().elements().markPersisted();
        content.model().relationships().markPersisted();
        content.views().forEach(this::save);
        flush("модель " + id);
    }

    @Override
    public void purge(ModelId id) {
        em.flush();
        em.createQuery("delete from ModelEntity m where m.id = :m").setParameter("m", id.value()).executeUpdate();
        em.clear();
    }

    @Override
    public Optional<ModelId> ownerOf(UUID objectId) {
        List<?> owners = em.createNativeQuery("""
                        select model_id from model_folder where id = :id
                        union all select model_id from element where id = :id
                        union all select model_id from relationship where id = :id
                        union all select model_id from view where id = :id
                        """)
                .setParameter("id", objectId).getResultList();
        return owners.stream().findFirst().map(o -> ModelId.of((UUID) o));
    }

    @Override
    public boolean idTaken(UUID id) {
        return !em.createNativeQuery("""
                        select 1 from model_folder where id = :id
                        union all select 1 from element where id = :id
                        union all select 1 from relationship where id = :id
                        union all select 1 from view where id = :id
                        union all select 1 from view_node where id = :id
                        union all select 1 from view_edge where id = :id
                        limit 1
                        """)
                .setParameter("id", id).getResultList().isEmpty();
    }

    @Override
    public boolean diagramArchiIdTaken(ModelId modelId, ArchiId archiId) {
        return !em.createNativeQuery("""
                        select 1 from view_node where model_id = :m and archi_id = :a
                        union all select 1 from view_edge where model_id = :m and archi_id = :a
                        limit 1
                        """)
                .setParameter("m", modelId.value()).setParameter("a", archiId.value()).getResultList().isEmpty();
    }

    @Override
    public List<ViewId> viewsReferencing(ElementId elementId) {
        return em.createQuery("select distinct n.viewId from ViewNodeEntity n where n.elementId = :e", UUID.class)
                .setParameter("e", elementId.value()).getResultList().stream().map(ViewId::of).toList();
    }

    @Override
    public List<ViewId> viewsReferencing(RelationshipId relationshipId) {
        return em.createQuery("select distinct x.viewId from ViewEdgeEntity x where x.relationshipId = :r", UUID.class)
                .setParameter("r", relationshipId.value()).getResultList().stream().map(ViewId::of).toList();
    }

    // ── Представления ───────────────────────────────────────────────

    @Override
    public Optional<View> load(ViewId id) {
        ViewEntity view = em.find(ViewEntity.class, id.value());
        if (view == null) {
            return Optional.empty();
        }
        var nodes = em.createQuery("select n from ViewNodeEntity n where n.viewId = :v", ViewNodeEntity.class)
                .setParameter("v", id.value()).getResultList();
        var edges = em.createQuery("select x from ViewEdgeEntity x where x.viewId = :v", ViewEdgeEntity.class)
                .setParameter("v", id.value()).getResultList();
        return Optional.of(View.restore(EntityMapper.toViewHeader(view),
                nodes.stream().map(EntityMapper::toNode).toList(), edges.stream().map(EntityMapper::toEdge).toList()));
    }

    /** Все представления модели тремя запросами — для снимка версии. */
    @Override
    public List<View> loadAll(ModelId modelId) {
        var views = em.createQuery(
                        "select distinct v from ViewEntity v left join fetch v.properties where v.modelId = :m",
                        ViewEntity.class)
                .setParameter("m", modelId.value()).getResultList();
        Map<UUID, List<ViewNodeEntity>> nodes = group(em.createQuery(
                        "select n from ViewNodeEntity n where n.modelId = :m", ViewNodeEntity.class)
                .setParameter("m", modelId.value()).getResultList(), n -> n.viewId);
        Map<UUID, List<ViewEdgeEntity>> edges = group(em.createQuery(
                        "select x from ViewEdgeEntity x where x.modelId = :m", ViewEdgeEntity.class)
                .setParameter("m", modelId.value()).getResultList(), x -> x.viewId);
        return views.stream()
                .map(v -> View.restore(EntityMapper.toViewHeader(v),
                        nodes.getOrDefault(v.id, List.of()).stream().map(EntityMapper::toNode).toList(),
                        edges.getOrDefault(v.id, List.of()).stream().map(EntityMapper::toEdge).toList()))
                .toList();
    }

    @Override
    public Optional<ViewId> viewOfNode(ViewNodeId nodeId) {
        return Optional.ofNullable(em.find(ViewNodeEntity.class, nodeId.value())).map(n -> ViewId.of(n.viewId));
    }

    /** Два запроса — пары «представление — элемент» и «представление — связь» без повторов. */
    @Override
    public List<ViewPlacements> placements(ModelId modelId) {
        Map<UUID, List<ElementId>> elements = new LinkedHashMap<>();
        em.createQuery("""
                        select distinct n.viewId, n.elementId from ViewNodeEntity n
                        where n.modelId = :m and n.elementId is not null
                        order by n.viewId, n.elementId
                        """, Object[].class)
                .setParameter("m", modelId.value()).getResultList()
                .forEach(row -> elements.computeIfAbsent((UUID) row[0], k -> new ArrayList<>())
                        .add(ElementId.of((UUID) row[1])));
        Map<UUID, List<RelationshipId>> relationships = new LinkedHashMap<>();
        em.createQuery("""
                        select distinct x.viewId, x.relationshipId from ViewEdgeEntity x
                        where x.modelId = :m and x.relationshipId is not null
                        order by x.viewId, x.relationshipId
                        """, Object[].class)
                .setParameter("m", modelId.value()).getResultList()
                .forEach(row -> relationships.computeIfAbsent((UUID) row[0], k -> new ArrayList<>())
                        .add(RelationshipId.of((UUID) row[1])));
        java.util.Set<UUID> viewIds = new java.util.LinkedHashSet<>(elements.keySet());
        viewIds.addAll(relationships.keySet());
        return viewIds.stream().map(v -> new ViewPlacements(ViewId.of(v), elements.getOrDefault(v, List.of()),
                relationships.getOrDefault(v, List.of()))).toList();
    }

    @Override
    public void save(View view) {
        UUID viewId = view.id().value();
        UUID modelId = view.modelId().value();
        if (view.fresh()) {
            ViewEntity entity = new ViewEntity();
            EntityMapper.apply(view.header(), entity);
            em.persist(entity);
        } else {
            ViewEntity entity = em.find(ViewEntity.class, viewId);
            if (entity == null) {
                throw new ConcurrentModificationException("представление " + view.id());
            }
            if (view.headerChanged()) {
                EntityMapper.apply(view.header(), entity);
            }
            if (view.changed()) {
                em.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            }
        }
        sync(view.nodes(), ViewNodeEntity.class, ViewNodeEntity::new,
                (d, e) -> EntityMapper.apply(d, modelId, viewId, e));
        sync(view.edges(), ViewEdgeEntity.class, ViewEdgeEntity::new,
                (d, e) -> EntityMapper.apply(d, modelId, viewId, e));
        flush("представление " + view.id());
    }

    @Override
    public void delete(ViewId id) {
        ViewEntity entity = em.find(ViewEntity.class, id.value());
        if (entity != null) {
            em.flush();
            em.createQuery("delete from ViewEdgeEntity x where x.viewId = :v").setParameter("v", id.value())
                    .executeUpdate();
            em.createQuery("delete from ViewNodeEntity n where n.viewId = :v").setParameter("v", id.value())
                    .executeUpdate();
            em.remove(entity);
            em.flush();
        }
    }

    // ── Общее ───────────────────────────────────────────────────────

    /** Тронутые сущности агрегата — в строки: новые вставляются, загруженные правятся, удалённые уходят. */
    private <K extends Record, V, E> void sync(TrackedMap<K, V> tracked, Class<E> type, Supplier<E> factory,
                                               Mapping<V, E> mapping) {
        for (K key : tracked.removals()) {
            E entity = em.find(type, idOf(key));
            if (entity != null) {
                em.remove(entity);
            }
        }
        for (Map.Entry<K, V> change : tracked.upserts().entrySet()) {
            E entity = tracked.isPersisted(change.getKey()) ? em.find(type, idOf(change.getKey())) : null;
            if (entity == null) {
                E created = factory.get();
                mapping.apply(change.getValue(), created);
                em.persist(created);
            } else {
                mapping.apply(change.getValue(), entity);
            }
        }
        tracked.markPersisted();
    }

    private <E> void persist(E entity, java.util.function.Consumer<E> fill) {
        fill.accept(entity);
        em.persist(entity);
    }

    private static UUID idOf(Record key) {
        try {
            return (UUID) key.getClass().getRecordComponents()[0].getAccessor().invoke(key);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ключ агрегата — record с UUID: " + key, e);
        }
    }

    private ModelEntity requireModel(ModelId id) {
        ModelEntity entity = em.find(ModelEntity.class, id.value());
        if (entity == null) {
            throw new ConcurrentModificationException("модель " + id);
        }
        return entity;
    }

    /**
     * Сброс сейчас, а не на коммите: конфликт версии должен стать отказом домена внутри
     * сценария, а не исключением транзакционного шаблона, которого сценарий не видит.
     */
    private void flush(String what) {
        try {
            em.flush();
        } catch (OptimisticLockException e) {
            throw new ConcurrentModificationException(what);
        } catch (PersistenceException e) {
            if (e.getCause() instanceof org.hibernate.StaleStateException) {
                throw new ConcurrentModificationException(what);
            }
            throw e;
        }
    }

    private static <T> Map<UUID, List<T>> group(List<T> rows, Function<T, UUID> key) {
        Map<UUID, List<T>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(key.apply(row), k -> new ArrayList<>()).add(row));
        return grouped;
    }

    @FunctionalInterface
    private interface Mapping<V, E> {
        void apply(V domain, E entity);
    }
}
