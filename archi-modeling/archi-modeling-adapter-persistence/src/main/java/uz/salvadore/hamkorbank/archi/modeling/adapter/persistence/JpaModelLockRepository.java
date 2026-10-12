package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.ModelLockEntity;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ConcurrentModificationException;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelLockRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Блокировки: первичный ключ {@code model_id} — одна на модель. Два одновременных захвата
 * свободной модели сходятся на вставке, второй получает нарушение ключа, то есть {@code 409}.
 */
@Repository
public class JpaModelLockRepository implements ModelLockRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<ModelLock> find(ModelId modelId) {
        return Optional.ofNullable(em.find(ModelLockEntity.class, modelId.value()))
                .map(e -> new ModelLock(ModelId.of(e.modelId), e.owner, e.acquiredAt, e.expiresAt));
    }

    @Override
    public void save(ModelLock lock) {
        ModelLockEntity entity = em.find(ModelLockEntity.class, lock.modelId().value());
        boolean fresh = entity == null;
        if (fresh) {
            entity = new ModelLockEntity();
            entity.modelId = lock.modelId().value();
        }
        entity.owner = lock.owner();
        entity.acquiredAt = lock.acquiredAt();
        entity.expiresAt = lock.expiresAt();
        try {
            if (fresh) {
                em.persist(entity);
            }
            em.flush();
        } catch (PersistenceException e) {
            throw new ConcurrentModificationException(Message.of(ModelingMessages.MODEL_LOCK, lock.modelId()));
        }
    }

    @Override
    public void delete(ModelId modelId) {
        ModelLockEntity entity = em.find(ModelLockEntity.class, modelId.value());
        if (entity != null) {
            em.remove(entity);
            em.flush();
        }
    }
}
