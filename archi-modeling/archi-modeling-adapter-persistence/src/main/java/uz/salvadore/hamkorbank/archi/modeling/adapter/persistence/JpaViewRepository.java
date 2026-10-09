package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ViewEdgeEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ViewEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ViewNodeEntity;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ConcurrentModificationException;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewPlacements;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/**
 * Хранение представлений: узлы и рёбра с геометрией. Все представления модели
 * читаются тремя запросами, а не по одному.
 *
 * <p>Любое изменение поднимает версию строки представления
 * ({@code OPTIMISTIC_FORCE_INCREMENT}): параллельная правка одного представления
 * становится конфликтом, а не тихой перезаписью.
 */
@Repository
public class JpaViewRepository implements ViewRepository {

    @PersistenceContext
    private EntityManager em;

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
                throw new ConcurrentModificationException(Message.of(ModelingMessages.VIEW, view.id()));
            }
            if (view.headerChanged()) {
                EntityMapper.apply(view.header(), entity);
            }
            if (view.changed()) {
                em.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            }
        }
        AggregateWrites.sync(em, view.nodes(), ViewNodeEntity.class, ViewNodeEntity::new,
                (d, e) -> EntityMapper.apply(d, modelId, viewId, e));
        AggregateWrites.sync(em, view.edges(), ViewEdgeEntity.class, ViewEdgeEntity::new,
                (d, e) -> EntityMapper.apply(d, modelId, viewId, e));
        AggregateWrites.flush(em, Message.of(ModelingMessages.VIEW, view.id()));
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


    private static <T> Map<UUID, List<T>> group(List<T> rows, Function<T, UUID> key) {
        Map<UUID, List<T>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(key.apply(row), k -> new ArrayList<>()).add(row));
        return grouped;
    }
}
