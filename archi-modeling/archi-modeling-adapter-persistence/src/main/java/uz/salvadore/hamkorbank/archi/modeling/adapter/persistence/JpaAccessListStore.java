package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.AccessEntryEntity;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelAccessListRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.ModelAccessList;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.PrincipalType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Списки доступа модели: запись заменяет список целиком. */
@Repository
public class JpaAccessListStore implements ModelAccessListRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public ModelAccessList find(ModelId modelId) {
        return findAll(List.of(modelId)).get(modelId);
    }

    @Override
    public Map<ModelId, ModelAccessList> findAll(Collection<ModelId> modelIds) {
        Map<ModelId, ModelAccessList> result = new HashMap<>();
        modelIds.forEach(id -> result.put(id, ModelAccessList.open(id)));
        if (modelIds.isEmpty()) {
            return result;
        }
        List<AccessEntryEntity> rows = em.createQuery(
                        "select a from AccessEntryEntity a where a.modelId in :ids order by a.sortOrder",
                        AccessEntryEntity.class)
                .setParameter("ids", modelIds.stream().map(ModelId::value).toList()).getResultList();
        rows.stream().collect(Collectors.groupingBy(a -> a.modelId)).forEach((UUID id, List<AccessEntryEntity> list) ->
                result.put(ModelId.of(id), new ModelAccessList(ModelId.of(id), list.stream()
                        .map(a -> new AclEntry(PrincipalType.valueOf(a.principalType), a.principal,
                                AclAccess.valueOf(a.access)))
                        .toList())));
        return result;
    }

    @Override
    public void save(ModelAccessList list) {
        em.createQuery("delete from AccessEntryEntity a where a.modelId = :m")
                .setParameter("m", list.modelId().value()).executeUpdate();
        int order = 0;
        for (AclEntry entry : list.entries()) {
            AccessEntryEntity row = new AccessEntryEntity();
            row.modelId = list.modelId().value();
            row.principalType = entry.principalType().name();
            row.principal = entry.principal();
            row.access = entry.access().name();
            row.sortOrder = order++;
            em.persist(row);
        }
        em.flush();
    }
}
