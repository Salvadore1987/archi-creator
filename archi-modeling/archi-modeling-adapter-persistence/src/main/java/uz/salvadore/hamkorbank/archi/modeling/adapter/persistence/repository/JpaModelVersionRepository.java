package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.repository;

import jakarta.persistence.EntityManager;
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
public class JpaModelVersionRepository implements ModelVersionRepository {

    private static final byte[] NOT_LOADED = new byte[0];

    private final EntityManager em;

    public JpaModelVersionRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<ModelVersion> find(ModelId modelId, long versionNo) {
        return em.createQuery("select v from ModelVersionEntity v where v.modelId = :m and v.versionNo = :n",
                        ModelVersionEntity.class)
                .setParameter("m", modelId.value()).setParameter("n", versionNo)
                .getResultStream().findFirst().map(JpaModelVersionRepository::toDomain);
    }

    @Override
    public Optional<ModelVersion> last(ModelId modelId) {
        return em.createQuery("select v from ModelVersionEntity v where v.modelId = :m order by v.versionNo desc",
                        ModelVersionEntity.class)
                .setParameter("m", modelId.value()).setMaxResults(1)
                .getResultStream().findFirst().map(JpaModelVersionRepository::toDomain);
    }

    @Override
    public List<ModelVersion> list(ModelId modelId) {
        return headers(modelId, Long.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public List<ModelVersion> page(ModelId modelId, long before, int size) {
        return headers(modelId, before, size);
    }

    /** Заголовки версий без снимков, от новых к старым: номер меньше {@code before}, не больше {@code size}. */
    private List<ModelVersion> headers(ModelId modelId, long before, int size) {
        return em.createQuery("""
                        select v.id, v.versionNo, v.author, v.comment, v.label, v.createdAt,
                               (case when v.snapshot is null then false else true end), v.contentHash, v.gitSha
                        from ModelVersionEntity v where v.modelId = :m and v.versionNo < :before
                        order by v.versionNo desc
                        """, Object[].class)
                .setParameter("m", modelId.value()).setParameter("before", before).setMaxResults(size)
                .getResultList().stream()
                .map(row -> ModelVersion.restore(VersionId.of((UUID) row[0]), modelId, (Long) row[1], (String) row[2],
                        (String) row[3], (String) row[4], (Instant) row[5],
                        Boolean.TRUE.equals(row[6]) ? NOT_LOADED : null, (String) row[7],
                        (String) row[8]))
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
            entity.comment = version.comment();
            entity.createdAt = version.createdAt();
            entity.contentHash = version.contentHash();
            entity.snapshot = version.snapshot();
            em.persist(entity);
        } else if (!version.hasSnapshot()) {
            entity.snapshot = null;
        } else {
            byte[] snapshot = version.requireSnapshot();
            if (snapshot.length > 0) {
                entity.snapshot = snapshot;
            }
        }
        entity.label = version.label();
        entity.gitSha = version.gitSha();
        em.flush();
    }

    @Override
    public List<ModelId> modelsWithSnapshots() {
        return em.createQuery("select distinct v.modelId from ModelVersionEntity v where v.snapshot is not null",
                UUID.class).getResultList().stream().map(ModelId::of).toList();
    }

    private static ModelVersion toDomain(ModelVersionEntity e) {
        return ModelVersion.restore(VersionId.of(e.id), ModelId.of(e.modelId), e.versionNo, e.author,
                e.comment, e.label, e.createdAt,
                e.snapshot, e.contentHash, e.gitSha);
    }
}
