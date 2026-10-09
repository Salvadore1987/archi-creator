package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ConcurrentModificationException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.TrackedMap;

/** Запись тронутых частей агрегата в строки — общая для модели и представлений. */
final class AggregateWrites {

    private AggregateWrites() {
    }

    @FunctionalInterface
    interface Mapping<V, E> {
        void apply(V domain, E entity);
    }

    /** Тронутые сущности агрегата — в строки: новые вставляются, загруженные правятся, удалённые уходят. */
    static <K extends Record, V, E> void sync(EntityManager em, TrackedMap<K, V> tracked, Class<E> type,
                                              Supplier<E> factory, Mapping<V, E> mapping) {
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

    /**
     * Сброс сейчас, а не на коммите: конфликт версии должен стать отказом домена внутри
     * сценария, а не исключением транзакционного шаблона, которого сценарий не видит.
     */
    static void flush(EntityManager em, Message what) {
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

    private static UUID idOf(Record key) {
        try {
            return (UUID) key.getClass().getRecordComponents()[0].getAccessor().invoke(key);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(Message.of(ModelingMessages.AGGREGATE_KEY_NOT_UUID, key).toString(), e);
        }
    }
}
