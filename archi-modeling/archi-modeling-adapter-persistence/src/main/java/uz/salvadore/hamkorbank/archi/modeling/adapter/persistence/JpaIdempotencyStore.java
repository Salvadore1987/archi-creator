package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity.IdempotencyEntity;
import uz.salvadore.hamkorbank.archi.modeling.application.port.IdempotencyRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotencyRecord;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;

/** Ключи идемпотентности. Гонка двух запросов с одним ключом — конфликт ключа, {@code 409}. */
@Repository
public class JpaIdempotencyStore implements IdempotencyRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<IdempotencyRecord> find(String scope, String actor, String key) {
        IdempotencyEntity.Key id = new IdempotencyEntity.Key();
        id.scope = scope;
        id.actor = actor;
        id.key = key;
        return Optional.ofNullable(em.find(IdempotencyEntity.class, id))
                .map(e -> new IdempotencyRecord(e.scope, e.actor, e.key, e.fingerprint, e.resultRef, e.createdAt));
    }

    @Override
    public void save(IdempotencyRecord record) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.scope = record.scope();
        entity.actor = record.actor();
        entity.key = record.key();
        entity.fingerprint = record.fingerprint();
        entity.resultRef = record.resultRef();
        entity.createdAt = record.createdAt();
        try {
            em.persist(entity);
            em.flush();
        } catch (PersistenceException e) {
            throw new ModelingException(IdempotentCommand.INVARIANT, Failure.CONFLICT,
                    "запрос с тем же ключом идемпотентности выполняется параллельно");
        }
    }
}
