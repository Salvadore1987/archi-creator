package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.WorkspaceEntity;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

@Repository
public class JpaWorkspaceRepository implements WorkspaceRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Workspace> findAll() {
        return em.createQuery("select w from WorkspaceEntity w order by w.createdAt, w.id", WorkspaceEntity.class)
                .getResultList().stream().map(JpaWorkspaceRepository::toDomain).toList();
    }

    @Override
    public Optional<Workspace> find(WorkspaceId id) {
        return Optional.ofNullable(em.find(WorkspaceEntity.class, id.value())).map(JpaWorkspaceRepository::toDomain);
    }

    @Override
    public void insert(Workspace workspace) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.id = workspace.id().value();
        entity.name = workspace.name();
        entity.createdAt = workspace.createdAt();
        em.persist(entity);
        em.flush();
    }

    private static Workspace toDomain(WorkspaceEntity e) {
        return new Workspace(WorkspaceId.of(e.id), e.name, e.createdAt);
    }
}
