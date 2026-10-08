package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ModelVersionEntity;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelVersionRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.VersionId;

/**
 * История версий. Список читается проекцией без {@code snapshot}: сотня версий по 150 КБ
 * в ответ «покажи историю» не нужна. Наличие снимка в списке передаётся пустым массивом —
 * маркером «снимок есть, но не загружен»; запись такого маркера столбец не трогает.
 */
@Repository
public class JpaVersionStore implements ModelVersionRepository {

    private static final byte[] NOT_LOADED = new byte[0];

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<ModelVersion> find(ModelId modelId, long versionNo) {
        return em.createQuery("select v from ModelVersionEntity v where v.modelId = :m and v.versionNo = :n",
                        ModelVersionEntity.class)
                .setParameter("m", modelId.value()).setParameter("n", versionNo)
                .getResultStream().findFirst().map(JpaVersionStore::toDomain);
    }

    @Override
    public Optional<ModelVersion> last(ModelId modelId) {
        return em.createQuery("select v from ModelVersionEntity v where v.modelId = :m order by v.versionNo desc",
                        ModelVersionEntity.class)
                .setParameter("m", modelId.value()).setMaxResults(1)
                .getResultStream().findFirst().map(JpaVersionStore::toDomain);
    }

    @Override
    public List<ModelVersion> list(ModelId modelId) {
        return em.createQuery("""
                        select v.id, v.versionNo, v.author, v.comment, v.label, v.createdAt,
                               (case when v.snapshot is null then false else true end), v.contentHash, v.gitSha
                        from ModelVersionEntity v where v.modelId = :m order by v.versionNo desc
                        """, Object[].class)
                .setParameter("m", modelId.value()).getResultList().stream()
                .map(row -> new ModelVersion(VersionId.of((UUID) row[0]), modelId, (Long) row[1], (String) row[2],
                        Optional.ofNullable((String) row[3]), Optional.ofNullable((String) row[4]), (Instant) row[5],
                        Boolean.TRUE.equals(row[6]) ? Optional.of(NOT_LOADED) : Optional.empty(), (String) row[7],
                        Optional.ofNullable((String) row[8])))
                .toList();
    }

    @Override
    public void save(ModelVersion version) {
        ModelVersionEntity entity = em.find(ModelVersionEntity.class, version.id().value());
        if (entity == null) {
            entity = new ModelVersionEntity();
            entity.id = version.id().value();
            entity.modelId = version.modelId().value();
            entity.versionNo = version.versionNo();
            entity.author = version.author();
            entity.comment = version.comment().orElse(null);
            entity.createdAt = version.createdAt();
            entity.contentHash = version.contentHash();
            entity.snapshot = version.snapshot().orElse(null);
            em.persist(entity);
        } else if (version.snapshot().isEmpty()) {
            entity.snapshot = null;
        } else if (version.snapshot().get().length > 0) {
            entity.snapshot = version.snapshot().get();
        }
        entity.label = version.label().orElse(null);
        entity.gitSha = version.gitSha().orElse(null);
        em.flush();
    }

    @Override
    public List<ModelId> modelsWithSnapshots() {
        return em.createQuery("select distinct v.modelId from ModelVersionEntity v where v.snapshot is not null",
                UUID.class).getResultList().stream().map(ModelId::of).toList();
    }

    private static ModelVersion toDomain(ModelVersionEntity e) {
        return new ModelVersion(VersionId.of(e.id), ModelId.of(e.modelId), e.versionNo, e.author,
                Optional.ofNullable(e.comment), Optional.ofNullable(e.label), e.createdAt,
                Optional.ofNullable(e.snapshot), e.contentHash, Optional.ofNullable(e.gitSha));
    }
}
