package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ElementEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.FolderEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ModelEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.RelationshipEntity;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ViewEntity;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ConcurrentModificationException;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Хранение модели: заголовок, папки, элементы, связи. Дерево читается по запросу
 * на таблицу — папки, элементы со свойствами, связи со свойствами, места представлений, —
 * а не обходом ассоциаций: число запросов не зависит от размера модели.
 *
 * <p>Пишется только то, что агрегат отметил изменённым. Любое изменение поднимает
 * версию строки модели ({@code OPTIMISTIC_FORCE_INCREMENT}): два параллельных писателя
 * одной модели не перезаписывают друг друга, даже если тронули разные элементы.
 * Замена содержимого целиком пишет и представления — через их репозиторий.
 */
@Repository
public class JpaModelRepository implements ModelRepository {

    @PersistenceContext
    private EntityManager em;

    private final ViewRepository views;

    public JpaModelRepository(ViewRepository views) {
        this.views = views;
    }

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
        AggregateWrites.sync(em, model.folders(), FolderEntity.class, FolderEntity::new, (d, e) -> EntityMapper.apply(d, modelId, e));
        AggregateWrites.sync(em, model.elements(), ElementEntity.class, ElementEntity::new, (d, e) -> EntityMapper.apply(d, modelId, e));
        AggregateWrites.sync(em, model.relationships(), RelationshipEntity.class, RelationshipEntity::new,
                (d, e) -> EntityMapper.apply(d, modelId, e));
        AggregateWrites.flush(em, Message.of(ModelingMessages.MODEL, model.id()));
    }

    @Override
    public void replaceContent(ModelContent content) {
        ModelId id = content.model().id();
        ModelEntity entity = requireModel(id);
        if (entity.version != content.model().version()) {
            throw new ConcurrentModificationException(Message.of(ModelingMessages.MODEL, id));
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
        content.views().forEach(views::save);
        AggregateWrites.flush(em, Message.of(ModelingMessages.MODEL, id));
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


    private ModelEntity requireModel(ModelId id) {
        ModelEntity entity = em.find(ModelEntity.class, id.value());
        if (entity == null) {
            throw new ConcurrentModificationException(Message.of(ModelingMessages.MODEL, id));
        }
        return entity;
    }

    private <E> void persist(E entity, java.util.function.Consumer<E> fill) {
        fill.accept(entity);
        em.persist(entity);
    }
}
